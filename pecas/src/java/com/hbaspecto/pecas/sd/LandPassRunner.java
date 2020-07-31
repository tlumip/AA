package com.hbaspecto.pecas.sd;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import com.hbaspecto.pecas.FormatLogger;
import com.hbaspecto.pecas.land.LandInventory;
import com.hbaspecto.pecas.sd.estimation.ConcurrentLandInventory;

/**
 * Class that can perform a single pass over a land inventory to do some
 * calculations on each parcel, either in a single thread or concurrently.
 */
public class LandPassRunner {

    private LandInventory land;
    private ParcelAction parcelAction;
    private long logFrequency;

    private static final FormatLogger logger = new FormatLogger(
            Logger.getLogger(LandPassRunner.class));

    public LandPassRunner(LandInventory land, ParcelAction parcelAction) {
        this(land, parcelAction, 1000);
    }

    public LandPassRunner(LandInventory land, ParcelAction parcelAction,
            long logFrequency) {
        this.land = land;
        this.parcelAction = parcelAction;
        this.logFrequency = logFrequency;
    }

    public void calculateInThisThread() throws LandPassException {
        land.setToBeforeFirst();
        long parcelCounter = 0;
        while (land.advanceToNext()) {
            parcelCounter++;
            if (parcelCounter % logFrequency == 0) {
                logger.info("finished parcel " + parcelCounter);
            }
            ZoningRulesI currentZoningRule = ZoningRulesI
                    .getZoningRuleByZoningRulesCode(land.getSession(),
                            land.getZoningRulesCode());
            if (currentZoningRule == null)
                land.getDevelopmentLogger().logBadZoning(land);
            else {
                parcelAction.perform(currentZoningRule);
            }
        }
    }

    public void calculateConcurrently(int concurrency)
            throws LandPassException {
        land.setToBeforeFirst();
        ConcurrentLandInventory cLand = (ConcurrentLandInventory) land;
        // Create and start worker threads that calculate the estimation matrix.
        Worker[] workers = new Worker[concurrency];
        Thread[] threads = new Thread[concurrency];
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicLong counter = new AtomicLong();
        for (int i = 0; i < concurrency; i++) {
            workers[i] = new Worker(cLand, parcelAction, latch, counter);
            Thread thread = new Thread(null, workers[i],
                    "ConcurrentLandWorker" + i);
            thread.start();
            threads[i] = thread;
        }
        // Load all the parcels from the database. The worker threads will
        // receive them as they are loaded.
        cLand.loadInventory(ZoningRulesI.currentYear, ZoningRulesI.baseYear);

        // Interrupt any worker threads that might be waiting on the queue, and
        // need to be told that no more parcels are coming.
        for (Thread thread : threads)
            thread.interrupt();
        // Wait for the worker threads to empty the queue.
        try {
            latch.await();
        } catch (InterruptedException e) {
            // Shouldn't happen.
            throw new RuntimeException(e);
        }

        for (Worker worker : workers) {
            LandPassException failure = worker.failureCause;
            if (failure != null) {
                throw failure;
            }
        }
    }

    // A worker thread used to calculate the values.
    private static class Worker implements Runnable {
        private ConcurrentLandInventory theLand;
        private ParcelAction theAction;
        private CountDownLatch theLatch;
        private AtomicLong theCounter;
        private volatile LandPassException failureCause = null;

        private Worker(ConcurrentLandInventory land, ParcelAction parcelAction,
                CountDownLatch latch, AtomicLong counter) {
            theLand = land;
            theAction = parcelAction;
            theLatch = latch;
            theCounter = counter;
        }

        @Override
        public void run() {
            try {
                while (theLand.advanceToNext()) {
                    long count = theCounter.incrementAndGet();
                    if (count % 1000 == 0) {
                        logger.info("finished parcel " + count);
                    }
                    ZoningRulesI currentZoningRule = ZoningRulesI
                            .getZoningRuleByZoningRulesCode(
                                    theLand.getSession(),
                                    theLand.getZoningRulesCode());
                    if (currentZoningRule == null)
                        theLand.getDevelopmentLogger().logBadZoning(theLand);
                    else {
                        // Synchronize to prevent concurrent modification of
                        // the ZoningRulesI object.
                        synchronized (currentZoningRule) {
                            theAction.perform(currentZoningRule);
                        }
                    }
                }
            } catch (LandPassException e) {
                failureCause = e;
                logger.error(
                        "Error in thread " + Thread.currentThread().getName(),
                        e);
                theLand.abort();
            } finally {
                theLatch.countDown();
                theLand.closeThreadLocalSession();
            }
        }
    }

    public static interface ParcelAction {
        public void perform(ZoningRulesI currentZoningRule)
                throws LandPassException;
    }

    @SuppressWarnings("serial")
    public static class LandPassException extends RuntimeException {

        public LandPassException() {
        }

        public LandPassException(String message) {
            super(message);
        }

        public LandPassException(Throwable cause) {
            super(cause);
        }

        public LandPassException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
