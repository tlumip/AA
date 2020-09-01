package omx;

import omx.hdf5.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * The {@code OmxFile} class provides read and write access to OMX files.
 *
 * @author crf
 *         Started 9/20/13 7:54 PM
 */
public class OmxFile extends AttributedElement implements AutoCloseable {
    private final Path filePath;
    private final OmxHdf5File hdf5File;
    private int[] shape = null;
    private final Map<String,OmxMatrix<?,?>> matrices;
    private final Map<String,OmxLookup<?,?>> lookups;
    private boolean writable = false;

    /**
     * Constructor specifying the path to the OMX file.
     *
     * @param filePath
     *        The path to the file.
     */
    public OmxFile(Path filePath) {
        this.filePath = filePath;
        hdf5File = new OmxHdf5File(filePath);
        matrices = new HashMap<>();
        lookups = new HashMap<>();
    }

    /**
     * Constructor specifying the path to the OMX file.
     *
     * @param filePath
     *        The path to the file.
     */
    public OmxFile(String filePath) {
        this(Paths.get(filePath));
    }

    /**
     * Get the path to the file this instance represents.
     *
     * @return the OMX file path.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Get the shape (dimensionality) of the matrix data this OMX file holds. If the file does not exist, this method will
     * return {@code null}.
     *
     * @return the shape of this file's matrix data, or {@code null} if the file does not exist.
     */
    public int[] getShape() {
        return Arrays.copyOf(shape,shape.length);
    }

    /**
     * Open the OMX file in read-only mode.
     *
     * @throws IllegalArgumentException if the file does not exist.
     * @throws IllegalStateException if the is already opened.
     */
    public void openReadOnly() {
        writable = false;
        hdf5File.openReadOnly();
        reload(false);
    }

    /**
     * Open the OMX file in read-write mode.
     *
     * @throws IllegalArgumentException if the file does not exist.
     * @throws IllegalStateException if the is already opened.
     */
    public void openReadWrite() {
        writable = true;
        hdf5File.openReadWrite();
        reload(false);
    }

    /**
     * Open the OMX file in new mode; this will create a new file or, if one already exists, replace it.
     *
     * @param shape
     *        The shape of the matrix data the new OMX file will hold.
     *
     * @throws IllegalStateException if the is already opened.
     */
    public void openNew(int[] shape) {
        writable = true;
        hdf5File.openNew(shape);
        reload(false);
    }

    private void checkOpened() {
        if (!hdf5File.isOpened())
            throw new IllegalStateException("File not opened: " + filePath);
    }

    private void checkWritable() {
        if (!writable)
            throw new IllegalStateException("File not writable: " + filePath);
    }

    /**
     * Reload the information/data held in this instance with that existing in actual file. This method should generally
     * not be needed unless an OMX file might be modified (while in use) by and external process.
     */
    public void reload() {
        reload(true);
    }

    private void reload(boolean reloadHdf5File) {
    	
        checkOpened();
        if (reloadHdf5File)
            hdf5File.reload();
        shape = hdf5File.getShape();
        OmxGroup baseGroup = hdf5File.getBaseGroup();
        Map<String,Object> attributes = baseGroup.getAttributes();
        for (String attribute : attributes.keySet())
            setAttribute(attribute,attributes.get(attribute));
        
    }

    /**
     * Save any changes made to the OMX data held by this instance to file. Checks will be made to see if any data has changed
     * on all {@code OmxMatrix} and {@code OmxLookup} instances provided by this {@code OmxFile} instance; if changes are
     * detected, they will be written to the file.
     */
    public void save() {
        checkOpened();
        checkWritable();
        OmxMutableGroup baseGroup = hdf5File.getBaseGroup().getMutableGroup();
        transferAttributes(baseGroup);
        //transfer datasets to data
        OmxMutableGroup dataGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_DATA_GROUP.getKey()).getMutableGroup();
        Collection<String> datasetNames = dataGroup.getDatasetNames();
        for (String name : matrices.keySet()) {
            if (datasetNames.contains(name)) {
                matrices.get(name).transfer(dataGroup.getDataset(name).getMutableDataset());
            } else {
                OmxMatrix<?,?> newMatrix = matrices.get(name);
                OmxDataset newDataset = new OmxMemoryDataset("/" + OmxConstants.OmxNames.OMX_DATA_GROUP.getKey() + "/" + name,shape,newMatrix.getOmxJavaType().getOmxHdf5Datatype(),newMatrix.getAttributes(),newMatrix.getData());
                dataGroup.setDataset(newDataset);
            }
        }
        //transfer lookups to lookup
        OmxMutableGroup lookupGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_LOOKUP_GROUP.getKey()).getMutableGroup();
        Collection<String> lookupNames = lookupGroup.getDatasetNames();
        for (String name : lookups.keySet()) {
            if (lookupNames.contains(name)) {
                lookups.get(name).transfer(lookupGroup.getDataset(name).getMutableDataset());
            } else {
                OmxLookup<?,?> newLookup = lookups.get(name);
                OmxDataset newDataset = new OmxMemoryDataset("/" + OmxConstants.OmxNames.OMX_LOOKUP_GROUP.getKey() + "/" + name,new int[] {newLookup.getLength()},newLookup.getOmxJavaType().getOmxHdf5Datatype(),newLookup.getAttributes(),newLookup.getLookup());
                lookupGroup.setDataset(newDataset);
            }
        }
        hdf5File.save();
        reload(false);
    }

    @Override
    public void close() {
        if (writable)
            save();
        hdf5File.close();
        //clear out data
        writable = false;
        shape = null;
        matrices.clear();
        lookups.clear();
    }

    /**
     * Get the OMX matrix held in this OMX file corresponding to a specific name.
     *
     * @param name
     *        The matrix name.
     *
     * @return the matrix named {@code name} held by this OMX file.
     *
     * @throws IllegalArgumentException if no matrix named {@code name} is found in this OMX file.
     * @throws IllegalStateException if this file is not opened.
     */
    public OmxMatrix<?,?> getMatrix(String name) {
        checkOpened();
        
        OmxGroup baseGroup = hdf5File.getBaseGroup();
        OmxGroup dataGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_DATA_GROUP.getKey());
        if(!matrices.containsKey(name)) { //only load if matrix doesn't exist
        		matrices.put(name,OmxMatrix.getMatrix(dataGroup.getDataset(name)));
        }
        if (!matrices.containsKey(name))
            throw new IllegalArgumentException("Matrix not found: " + name);
        return matrices.get(name);
    }

    /**
     * Get the set of all matrix names held in this OMX file.
     *
     * @return the set of names of all matrices held in this OMX file.
     *
     * @throws IllegalStateException if this file is not opened.
     */
    public Set<String> getMatrixNames() {
        checkOpened();
        OmxGroup baseGroup = hdf5File.getBaseGroup();
        OmxGroup dataGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_DATA_GROUP.getKey());
        return (Set<String>) new HashSet<String>(dataGroup.getDatasetNames());
    }

    /**
     * Add a matrix to this OMX file, using its name as an identifier. Note that if a matrix already exists in the file
     * with the same name, it will be overwritten by this method.
     *
     * @param matrix
     *        The matrix to add.
     *
     * @throws IllegalStateException if the file is not opened or is not opened for writable access.
     */
    public void addMatrix(OmxMatrix<?,?> matrix) {
        checkOpened();
        checkWritable();
        //note: if matrix already exists, it will be written over!
        int[] matrixShape = matrix.getShape();
        if ((matrixShape[0] != shape[0]) || (matrixShape[1] != shape[1]))
            throw new IllegalArgumentException("Matrix dimension (" + Arrays.toString(matrixShape) + ") does not match file dimensions (" + Arrays.toString(shape) + ")");
        matrices.put(matrix.getName(),matrix);
    }

    /**
     * Delete the OMX matrix held in this OMX file corresponding to a specific name.
     *
     * @param name
     *        The name of the matrix to delete.
     *
     * @throws IllegalArgumentException if no matrix named {@code name} is found in this OMX file.
     * @throws IllegalStateException if the file is not opened or is not opened for writable access.
     */
    public void deleteMatrix(String name) {
        checkOpened();
        checkWritable();
        if (!matrices.containsKey(name))
            throw new IllegalArgumentException("Matrix not found: " + name);
        matrices.remove(name);
        
        OmxMutableGroup baseGroup = hdf5File.getBaseGroup().getMutableGroup();
        OmxMutableGroup dataGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_DATA_GROUP.getKey()).getMutableGroup();
        if (!dataGroup.getDatasetNames().contains(name))
                throw new IllegalArgumentException("Matrix not found: " + name);
        dataGroup.deleteDataset(name);
    }

    /**
     * Get the OMX lookup held in this OMX file corresponding to a specific name.
     *
     * @param name
     *        The lookup name.
     *
     * @return the lookup named {@code name} held by this OMX file.
     *
     * @throws IllegalArgumentException if no lookup named {@code name} is found in this OMX file.
     * @throws IllegalStateException if this file is not opened.
     */
    public OmxLookup<?,?> getLookup(String name) {
        checkOpened();
        
        OmxGroup baseGroup = hdf5File.getBaseGroup();
        OmxGroup lookupGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_LOOKUP_GROUP.getKey());
        if(!lookups.containsKey(name)) { //only load if lookup doesn't exist
        		lookups.put(name,OmxLookup.getLookup(lookupGroup.getDataset(name)));
        }
        if (!lookups.containsKey(name))
            throw new IllegalArgumentException("Lookup not found: " + name);
        return lookups.get(name);
    }

    /**
     * Get the set of all lookup names held in this OMX file.
     *
     * @return the set of names of all lookups held in this OMX file.
     *
     * @throws IllegalStateException if this file is not opened.
     */
    public Set<String> getLookupNames() {
        checkOpened();
        OmxGroup baseGroup = hdf5File.getBaseGroup();
        OmxGroup lookupGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_LOOKUP_GROUP.getKey());
        return (Set<String>) new HashSet<String>(lookupGroup.getDatasetNames());
    }

    /**
     * Add a lookup to this OMX file, using its name as an identifier. Note that if a lookup already exists in the file
     * with the same name, it will be overwritten by this method.
     *
     * @param lookup
     *        The lookup to add.
     *
     * @throws IllegalArgumentException if the lookup length does not correspond to the size of at least on of the dimensions
     *                                  of matrices held by this file.
     * @throws IllegalStateException if the file is not opened or is not opened for writable access.
     */
    public void addLookup(OmxLookup<?,?> lookup) {
        checkOpened();
        checkWritable();
        //note: if lookup already exists, it will be written over!
        int lookupLength = lookup.getLength();
        if ((lookupLength != shape[0]) && (lookupLength != shape[1]))
            throw new IllegalArgumentException("Lookup length (" + lookupLength + ") does not match to either file dimension (" + Arrays.toString(shape) + ")");
        lookups.put(lookup.getName(),lookup);
    }

    /**
     * Delete the OMX matrix held in this OMX file corresponding to a specific name.
     *
     * @param name
     *        The name of the matrix to delete.
     *
     * @throws IllegalArgumentException if no matrix named {@code name} is found in this OMX file.
     * @throws IllegalStateException if the file is not opened or is not opened for writable access.
     */
    public void deleteLookup(String name) {
        checkOpened();
        checkWritable();
        if (!lookups.containsKey(name))
            throw new IllegalArgumentException("Lookup not found: " + name);
        lookups.remove(name);
        
        OmxMutableGroup baseGroup = hdf5File.getBaseGroup().getMutableGroup();
        OmxMutableGroup lookupGroup = baseGroup.getGroup(OmxConstants.OmxNames.OMX_LOOKUP_GROUP.getKey()).getMutableGroup();
        if (!lookupGroup.getDatasetNames().contains(name))
                throw new IllegalArgumentException("Lookup not found: " + name);
        lookupGroup.deleteDataset(name);
    }

    /**
     * Get a text summary of the contents of this OMX file.
     *
     * @return a summary of this OMX file's contents.
     */
    public String summary() {
        checkOpened();
        StringBuilder sb = new StringBuilder();
        sb.append("OMX file: ").append(filePath).append("\n");
        sb.append("\tshape: ").append(Arrays.toString(shape)).append("\n");
        sb.append("\tmatrices:\n");
        int count = 1;
        for (String name : getMatrixNames()) {
            sb.append("\t\tmatrix ").append(count++).append(": ").append(name).append("\n");
            OmxMatrix<?,?> matrix = getMatrix(name);
            sb.append("\t\t\tshape: ").append(Arrays.toString(matrix.getShape())).append("\n");
            sb.append("\t\t\ttype: ").append(matrix.getOmxJavaType()).append("\n");
            sb.append("\t\t\tattributes:").append("\n");
            for (String attribute : matrix.getAttributeKeys())
                sb.append("\t\t\t\t").append(attribute).append(" : ").append(OmxUtil.objectToString(matrix.getAttribute(attribute))).append("\n");
        }
        sb.append("\tlookups:\n");
        count = 1;
        for (String name : getLookupNames()) {
            sb.append("\t\tlookup ").append(count++).append(": ").append(name).append("\n");
            OmxLookup<?,?> lookup = getLookup(name);
            sb.append("\t\t\tlength: ").append(lookup.getLength()).append("\n");
            sb.append("\t\t\ttype: ").append(lookup.getOmxJavaType()).append("\n");
            sb.append("\t\t\tattributes:").append("\n");
            for (String attribute : lookup.getAttributeKeys())
                sb.append("\t\t\t\t").append(attribute).append(" : ").append(OmxUtil.objectToString(lookup.getAttribute(attribute))).append("\n");
        }
        return sb.toString();
    }

    /**
     * Repack an OMX file to a new file. This method is useful because deleting elements in a OMX (HDF5) file does not
     * necessarily delete the data from the file, just the links to it. This means a file which has had data deleted from
     * it may be larger (on disk) than necessary. This method essentially loads the file data, and the rewrites a new file
     * with the data.
     *
     * @param sourceFile
     *        The source OMX file.
     *
     * @param destinationFile
     *        The destination OMX file.
     */
    public static void repack(String sourceFile, String destinationFile) {
        repack(Paths.get(sourceFile),Paths.get(destinationFile));
    }

    /**
     * Repack an OMX file to a new file. This method is useful because deleting elements in a OMX (HDF5) file does not
     * necessarily delete the data from the file, just the links to it. This means a file which has had data deleted from
     * it may be larger (on disk) than necessary. This method essentially loads the file data, and the rewrites a new file
     * with the data.
     *
     * @param sourceFile
     *        The source OMX file.
     *
     * @param destinationFile
     *        The destination OMX file.
     */
    public static void repack(Path sourceFile, Path destinationFile) {
        try (OmxFile sourceOmxFile = new OmxFile(sourceFile);
             OmxFile destinationOmxFile = new OmxFile(destinationFile)) {
            sourceOmxFile.openReadOnly();
            destinationOmxFile.openNew(sourceOmxFile.getShape());
            for (String attribute : sourceOmxFile.getAttributeKeys())
                if (!destinationOmxFile.hasAttribute(attribute))
                    destinationOmxFile.setAttribute(attribute,sourceOmxFile.getAttribute(attribute));
            for (String matrixName : sourceOmxFile.getMatrixNames())
                destinationOmxFile.addMatrix(sourceOmxFile.getMatrix(matrixName));
            for (String lookupName : sourceOmxFile.getLookupNames())
                destinationOmxFile.addLookup(sourceOmxFile.getLookup(lookupName));
        }
    }

    /**
     *
     * Repack an OMX file. This method is useful because deleting elements in a OMX (HDF5) file does not necessarily delete
     * the data from the file, just the links to it. This means a file which has had data deleted from it may be larger
     * (on disk) than necessary. This method essentially loads the file data, repacks it to a new temporary file, and then
     * replaces the existing file with the newly created one.
     *
     * @param file
     *        The OMX file to repack.
     */
    public static void repack(String file) {
        repack(Paths.get(file));
    }

    /**
     *
     * Repack an OMX file. This method is useful because deleting elements in a OMX (HDF5) file does not necessarily delete
     * the data from the file, just the links to it. This means a file which has had data deleted from it may be larger
     * (on disk) than necessary. This method essentially loads the file data, repacks it to a new temporary file, and then
     * replaces the existing file with the newly created one.
     *
     * @param file
     *        The OMX file to repack.
     */
    public static void repack(Path file) {
        Path tempFile = Paths.get(file.toString() + new Random().nextInt(100) + ".tmp");
        repack(file,tempFile);
        try {
            Files.copy(tempFile,file,StandardCopyOption.REPLACE_EXISTING);
            Files.delete(tempFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String ... args) {
        
    	Random r = new Random();
        String f = "example.omx";
        try (OmxFile omxFile = new OmxFile(f)) {
            
        	int dim0 = 2000;
        	
            int dim1 = dim0;
            int[] shape = {dim0,dim1};

            double mat1NA = -1;
            double[][] mat1Data = new double[dim0][dim1];
            for (int i = 0; i < dim0; i++)
                for (int j = 0; j < dim1; j++)
                    mat1Data[i][j] = i+j;
            OmxMatrix.OmxDoubleMatrix mat1 = new OmxMatrix.OmxDoubleMatrix("mat1",mat1Data,mat1NA);
            mat1.setAttribute(OmxConstants.OmxNames.OMX_DATASET_TITLE_KEY.getKey(),"an int matrix");

            double lookup2NA = -99999;
            double[] lookup2Data = new double[dim1];
            Set<Double> lookup2Used = new HashSet<>();
            for (int i = 0; i < lookup2Data.length; i++) {
            	double lookup = i+1;
                lookup2Data[i] = lookup2Used.add(lookup) ? lookup : lookup2NA;
            }
            OmxLookup.OmxDoubleLookup lookup2 = new OmxLookup.OmxDoubleLookup("lookup2",lookup2Data,lookup2NA);

            omxFile.openNew(shape);
            omxFile.addMatrix(mat1);
            omxFile.addLookup(lookup2);
            omxFile.save();
            System.out.println(omxFile.summary());
            
        }

        try (OmxFile omxFile = new OmxFile(f)) {
            omxFile.openReadWrite();
            System.out.println(omxFile.summary());
            omxFile.deleteMatrix("mat1");
            omxFile.deleteLookup("lookup2");
            System.out.println(omxFile.summary());
        }
        
    }
}
