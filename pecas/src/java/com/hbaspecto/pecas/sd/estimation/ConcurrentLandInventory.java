package com.hbaspecto.pecas.sd.estimation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import simpleorm.dataset.SQuery;
import simpleorm.sessionjdbc.SSessionJdbc;

import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.land.ParcelErrorLog;
import com.hbaspecto.pecas.land.ParcelInterface;
import com.hbaspecto.pecas.land.SimpleORMLandInventory;
import com.hbaspecto.pecas.sd.ChoiceUtilityLog;
import com.hbaspecto.pecas.sd.DevelopmentLog;
import com.hbaspecto.pecas.sd.SpaceTypesI;
import com.pb.common.datafile.TableDataFileReader;
import com.pb.common.datafile.TableDataFileWriter;
import com.pb.common.datafile.TableDataSet;

/**
 * A read-only wrapper for a land inventory that allows different threads to
 * access different parcels. Each thread has its own internal counter that
 * determines the current parcel, which gets advanced by
 * <code>advanceToNext()</code> as normal; however, accessing threads should not
 * use the <code>setToBeforeFirst()</code> method, as this will reset the
 * inventory for all threads. All methods that would normally modify the
 * underlying data structure instead throw an
 * <code>UnsupportedOperationException</code>.
 * 
 * @author Graham Hill
 * 
 */
public final class ConcurrentLandInventory implements LandInventory {

	// The inventory that's being wrapped.
	private final LandInventory inner;
	private final ThreadLocal<Parcel> currentParcel;
	private final ThreadLocal<SSessionJdbc> session;
	private final BlockingQueue<Parcel> parcelQueue;
	private List<SpaceTypesI> spacetypes;
	private volatile int fixedCurrentYear;
	private volatile int fixedBaseYear;
	private volatile boolean doneLoadingParcels;

	// A private lock object to synchronize on.
	private final Object feedLock = new Object();
	private final Object accessLock = new Object();

	/**
	 * Constructs a <code>ConcurrentLandInventory</code> that wraps the
	 * specified land inventory.
	 * 
	 * @param land
	 *            The land inventory.
	 */
	public ConcurrentLandInventory(LandInventory land, int queueSize, ResourceBundle rb) {
		inner = land;
		if (inner == null)
			throw new NullPointerException();
		currentParcel = new ThreadLocal<Parcel>();
		parcelQueue = new LinkedBlockingQueue<Parcel>(queueSize);

		session = new ThreadLocal<SSessionJdbc>() {
			@Override
			public SSessionJdbc initialValue() {
				return SimpleORMLandInventory
						.prepareAdditionalSimpleORMSession(Thread
								.currentThread().getName(), rb);
			}
		};
	}

	/**
	 * Iterates through the wrapped land inventory, making each parcel available
	 * in sequence to the outer land inventory. The thread that calls this
	 * method should not be one of the threads that access the outer inventory.
	 * When this method returns, all accessing threads should be interrupted to
	 * make sure that they stop waiting for parcels. The
	 * <code>currentYear</code> and <code>baseYear</code> parameters commit this
	 * land inventory to accepting only those years in the
	 * <code>getPrices()</code> method until the next call to
	 * <code>loadInventory()</code>.
	 * 
	 * @param currentYear
	 *            The current simulation year.
	 * @param baseYear
	 *            The base year.
	 */
    public void loadInventory(int currentYear, int baseYear) {
        try {
            synchronized (feedLock) {
                fixedCurrentYear = currentYear;
                fixedBaseYear = baseYear;
                SQuery<SpaceTypesI> query = new SQuery<SpaceTypesI>(
                        SpaceTypesI.meta);
                spacetypes = inner.getSession().query(query);
                while (inner.advanceToNext()) {
                    if(doneLoadingParcels) {
                        inner.abort();
                        break;
                    }
                    Parcel parcel = new Parcel();
                    parcel.availableServiceCode = inner
                            .getAvailableServiceCode();
                    parcel.costScheduleId = inner.get_CostScheduleId();
                    parcel.coverage = inner.getCoverage();
                    parcel.feeScheduleId = inner.get_FeeScheduleId();
                    parcel.isBrownfield = inner.isBrownfield();
                    parcel.isDerelict = inner.isDerelict();
                    parcel.isDevelopable = inner.isDevelopable();
                    parcel.landArea = inner.getLandArea();
                    parcel.maxParcelSize = inner.getMaxParcelSize();
                    parcel.parcelId = inner.getParcelId();
                    parcel.pecasParcelNumber = inner.getPECASParcelNumber();
                    parcel.quantity = inner.getQuantity();
                    parcel.taz = inner.getTaz();
                    parcel.toString = inner.parcelToString();
                    parcel.yearBuilt = inner.getYearBuilt();
                    parcel.zoningRulesCode = inner.getZoningRulesCode();
                    parcel.prices = getPrices();
                    parcelQueue.put(parcel);
                }
            }
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            doneLoadingParcels = true;
        }
	}

	private Map<Integer, Double> getPrices() {
		Map<Integer, Double> result = new HashMap<Integer, Double>();
		for (SpaceTypesI spacetype : spacetypes) {
			int coverage = spacetype.getSpaceTypeID();
			double price = inner.getPrice(coverage, fixedCurrentYear,
					fixedBaseYear);
			result.put(coverage, price);
		}
		return result;
	}

	@Override
	public void setToBeforeFirst() {
		synchronized (feedLock) {
			inner.setToBeforeFirst();
			parcelQueue.clear();
			doneLoadingParcels = false;
		}
	}

	@Override
	public boolean advanceToNext() {
		if (doneLoadingParcels) {
			// No more parcels will be coming!
			// So take parcels if they're in the queue, but don't wait around if
			// the queue is empty.
			Parcel parcel = parcelQueue.poll();
			if (parcel == null)
				return false;
			currentParcel.set(parcel);
			return true;
		} else {
			// Take the next parcel, waiting if necessary for loadInventory() to
			// add a parcel to the queue.
			while (true) {
				try {
					currentParcel.set(parcelQueue.take());
					return true;
				} catch (InterruptedException e) {
					// The loadInventory() method may have just finished, in
					// which case, stop waiting around for more parcels.
					if (doneLoadingParcels)
						return false;
				}
			}
		}
	}
	
    @Override
    public void abort() {
        doneLoadingParcels = true;
    }

	@Override
	public DevelopmentLog getDevelopmentLogger() {
		synchronized (accessLock) {
			return inner.getDevelopmentLogger();
		}
	}
	
	@Override
	public ChoiceUtilityLog getChoiceUtilityLogger() {
	    synchronized (accessLock) {
	        return inner.getChoiceUtilityLogger();
	    }
	}

	@Override
	public ParcelErrorLog getParcelErrorLog() {
		synchronized (accessLock) {
			return inner.getParcelErrorLog();
		}
	}

	@Override
	public void putCoverage(int coverageCode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putQuantity(double quantity) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putYearBuilt(int yearBuilt) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAvailableServiceCode(int service) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getYearBuilt() {
		return currentParcel.get().yearBuilt;
	}

	@Override
	public double getQuantity() {
		return currentParcel.get().quantity;
	}

	@Override
	public int getCoverage() {
		return currentParcel.get().coverage;
	}

	@Override
	public double getLandArea() {
		return currentParcel.get().landArea;
	}

	@Override
	public int getAvailableServiceCode() {
		return currentParcel.get().availableServiceCode;
	}

	@Override
	public int getZoningRulesCode() {
		return currentParcel.get().zoningRulesCode;
	}

	@Override
	public double getPrice(int coverageCode, int currentYear, int baseYear) {
		if (currentYear != fixedCurrentYear || baseYear != fixedBaseYear)
			throw new IllegalArgumentException();
		return currentParcel.get().prices.get(coverageCode);
	}

	@Override
	public String parcelToString() {
		return currentParcel.get().toString;
	}

	@Override
	public boolean isDevelopable() {
		return currentParcel.get().isDevelopable;
	}

	@Override
	public boolean isDerelict() {
		return currentParcel.get().isDerelict;
	}

	@Override
	public TableDataSet summarizeInventory() {
		synchronized (accessLock) {
			return inner.summarizeInventory();
		}
	}

	@Override
	public String getParcelId() {
		return currentParcel.get().parcelId;
	}

	@Override
	public double getMaxParcelSize() {
		return currentParcel.get().maxParcelSize;
	}

	@Override
	public void setMaxParcelSize(double maxParcelSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ParcelInterface splitParcel(double parcelSizes)
			throws NotSplittableException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addNewBits() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void applyDevelopmentChanges() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int get_CostScheduleId() {
		return currentParcel.get().costScheduleId;
	}

	@Override
	public int get_FeeScheduleId() {
		return currentParcel.get().feeScheduleId;
	}

	@Override
	public long getPECASParcelNumber() {
		return currentParcel.get().pecasParcelNumber;
	}

	@Override
	public boolean isBrownfield() {
		return currentParcel.get().isBrownfield;
	}

	@Override
	public void init(int year) {
		synchronized (accessLock) {
			inner.init(year);
		}
	}

	@Override
	public int getTaz() {
		return currentParcel.get().taz;
	}

	@Override
	public void readSpacePrices(TableDataFileReader reader) {
		synchronized (accessLock) {
			inner.readSpacePrices(reader);
		}
	}

	@Override
	public void applyPriceSmoothing(TableDataFileReader reader,
			TableDataFileWriter writer) {
		synchronized (accessLock) {
			inner.applyPriceSmoothing(reader, writer);
		}
	}

	@Override
	public void putDerelict(boolean isDerelict) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putBrownfield(boolean b) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SSessionJdbc getSession() {
		return session.get();
	}

	@Override
	public void disconnect() {
		synchronized (accessLock) {
			inner.disconnect();
		}
	}

	@Override
	public void commitAndStayConnected() {
		synchronized (accessLock) {
			inner.commitAndStayConnected();
		}
	}

	/**
	 * Closes the session opened for this thread
	 */
    public void closeThreadLocalSession() {
        SSessionJdbc session = getSession();
        if (session.hasBegun()) {
            session.commit();
        }
        session.close();
    }

	private static class Parcel {
		// One field for each land inventory getter method.
		private int yearBuilt;
		private double quantity;
		private int coverage;
		private double landArea;
		private int availableServiceCode;
		private int zoningRulesCode;
		private String toString;
		private boolean isDevelopable;
		private boolean isDerelict;
		private String parcelId;
		private double maxParcelSize;
		private int costScheduleId;
		private int feeScheduleId;
		private long pecasParcelNumber;
		private boolean isBrownfield;
		private int taz;

		private Map<Integer, Double> prices;

		@Override
		public String toString() {
			return toString;
		}
	}
}
