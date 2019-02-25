package amuse.nodes.validator.methods;

import amuse.data.MeasureTable;
import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.validator.ValidationConfiguration;
import amuse.nodes.validator.interfaces.*;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;
import com.rapidminer.Process;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.set.SimpleExampleSet;
import com.rapidminer.example.table.DataRow;
import com.rapidminer.example.table.DoubleArrayDataRow;
import com.rapidminer.example.table.ExampleTable;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.ModelApplier;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.ExtractClusterPrototypes;
import com.rapidminer.operator.io.ClusterModelReader;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.OperatorService;
import org.apache.log4j.Level;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Performs cluster distance validation
 *
 * @author Laurene Cladt
 * @version $Id$
 */
public class ClusterDistanceEvaluator extends AmuseTask implements ValidatorInterface {

    /** Measure calculator used by this validator */
    private ArrayList<MeasureCalculatorInterface> measureCalculators = new ArrayList<MeasureCalculatorInterface>();

    /** Ids of measures to calculate */
    private ArrayList<Integer> measureIds = new ArrayList<Integer>();

    /** Path to a single model file or folder with several models which should be evaluated */
    private String pathToModelFile = null;

    /*
     * (non-Javadoc)
     * @see amuse.interfaces.AmuseTaskInterface#initialize()
     */
    public void initialize() throws NodeException {
        // Do nothing, since initialization is not required
        //TODO: ??
        try {
            LibraryInitializer.initializeRapidMiner();
        } catch (Exception e) {
            throw new NodeException("Could not initialize RapidMiner: " + e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see amuse.interfaces.AmuseTaskInterface#setParameters(java.lang.String)
     */
    public void setParameters(String parameterString) throws NodeException {
        if(parameterString.startsWith("\"") || parameterString.startsWith("'") || parameterString.startsWith("|") && parameterString.endsWith("|")) {
            this.pathToModelFile = parameterString.substring(1,parameterString.length()-1);
        } else {
            this.pathToModelFile = parameterString;
        }
    }

    /**
     * Performs validation of the given model(s)
     */
    public void validate() throws NodeException {

        // --------------------------------
        // (I) Configure measure calculators
        // --------------------------------
        try {
            configureMeasureCalculators();
        } catch(NodeException e) {
            throw e;
        }

        // -----------------------
        // (II) Perform evaluation
        // -----------------------
        try {
            performEvaluation();
        } catch(NodeException e) {
            throw e;
        }
    }

    /**
     * Configures measure calculators
     * @throws NodeException
     */
    private void configureMeasureCalculators() throws NodeException {

        // TODO Support measure calculators which use some parameters (like F-Measure) -> similar to algorithms
        try {
            MeasureTable mt = ((ValidationConfiguration)this.correspondingScheduler.getConfiguration()).getMeasures();
            for(int i=0;i<mt.size();i++) {

                // Set measure method properties
                Class<?> measureMethod = Class.forName(mt.get(i).getMeasureClass());
                MeasureCalculatorInterface vmc = (MeasureCalculatorInterface)measureMethod.newInstance();
                this.measureCalculators.add(vmc);
                this.measureIds.add(mt.get(i).getID());
                if(vmc instanceof ClassificationQualityMeasureCalculatorInterface) {
                    if(mt.get(i).isPartitionLevelSelected()) {
                        ((ClassificationQualityMeasureCalculatorInterface)vmc).setPartitionLevel(true);
                    }
                    if(mt.get(i).isSongLevelSelected()) {
                        ((ClassificationQualityMeasureCalculatorInterface)vmc).setSongLevel(true);
                    }
                }
            }
        } catch(Exception e) {
            throw new NodeException("Configuration of measure method for validation failed: " + e.getMessage());
        }

        // Check if any measure calculators are loaded
        if(this.measureCalculators.size() == 0) {
            throw new NodeException("No measure method could be loaded for validation");
        }
    }

    /**
     * Performs model evaluation
     * @throws NodeException
     */
    private void performEvaluation() throws NodeException {
        //TODO

        // List of all models to evaluate
        ArrayList<File> modelsToEvaluate = new ArrayList<File>();
        File modelFile = new File(pathToModelFile);

        // If a folder is given, search for all models
        if(modelFile.isDirectory()) {
            File[] files = modelFile.listFiles();

            // Go through all files in the given directory
            for(int i=0;i<files.length;i++) {
                if(files[i].isFile() && files[i].toString().endsWith(".mod")) {
                    modelsToEvaluate.add(files[i]);
                }
            }
        }
        // If a single model file is given, load it to the list
        else {
            modelsToEvaluate.add(modelFile);
        }
        AmuseLogger.write(this.getClass().getName(), Level.INFO, modelsToEvaluate.size() + " model(s) will be evaluated");

        // Validation measures are saved in a list (for each run)
        ArrayList<ArrayList<ValidationMeasure>> measuresForEveryModel = new ArrayList<ArrayList<ValidationMeasure>>();

        // Load the ground truth as dataset in order to run a correct classification
        // The ground truth has to be the same which the model was trained with
        //TODO
        DataSet dataSet = ((DataSetInput)((ValidationConfiguration) this.correspondingScheduler.getConfiguration()).getInputToValidate()).getDataSet();
        ExampleSet exampleSet = dataSet.convertToRapidMinerExampleSet();

        // Go through all models which should be evaluated
        for(int i=0;i<modelsToEvaluate.size();i++) {
            //TODO
            try {
                Process process = new Process();

                // Load the model
                Operator modelLoader = OperatorService.createOperator(ClusterModelReader.class);
                modelLoader.setParameter("cluster_model_file", modelsToEvaluate.get(i).getAbsolutePath()); //pathToModelFile
                process.getRootOperator().getSubprocess(0).addOperator(modelLoader);

                // Prepare cluster centroid extraction
                Operator clusterExtractor = OperatorService.createOperator(ExtractClusterPrototypes.class);
                process.getRootOperator().getSubprocess(0).addOperator(clusterExtractor);

                Operator modelApplier = OperatorService.createOperator(ModelApplier.class);
                process.getRootOperator().getSubprocess(0).addOperator(modelApplier);

                InputPort clusterExtractorInputPort = clusterExtractor.getInputPorts().getPortByName("model");
                OutputPort modelLoaderOutputPort = modelLoader.getOutputPorts().getPortByName("output");
                OutputPort clusterExtractorOutputPort = clusterExtractor.getOutputPorts().getPortByName("example set");
                OutputPort clusterExtractorModelOutputPort = clusterExtractor.getOutputPorts().getPortByName("model");

                // Get centroids as result
                InputPort processInputPort = process.getRootOperator().getSubprocess(0).getInnerSinks().getPortByIndex(0);

                modelLoaderOutputPort.connectTo(clusterExtractorInputPort);

                clusterExtractorOutputPort.connectTo(processInputPort);

                // Get cluster assignments
                InputPort modelApplierModelInputPort = modelApplier.getInputPorts().getPortByName("model");
                InputPort modelApplierUnlabelledDataInputPort = modelApplier.getInputPorts().getPortByName("unlabelled data");
                OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);
                OutputPort modelApplierOutputPort = modelApplier.getOutputPorts().getPortByName("labelled data");

                clusterExtractorModelOutputPort.connectTo(modelApplierModelInputPort);
                InputPort processInputPort2 = process.getRootOperator().getSubprocess(0).getInnerSinks().getPortByIndex(1);
                processOutputPort.connectTo(modelApplierUnlabelledDataInputPort);
                modelApplierOutputPort.connectTo(processInputPort2);


                // Run the process
                // result container contains centroids and cluster assigments
                IOContainer result = process.run(new IOContainer(exampleSet));

                // For each data, calculate the distance between it's features and the cluster's centroid
                //TODO
                // save each centroid to a table with the cluster number
                ExampleTable centroidTable = ((SimpleExampleSet) result.getElementAt(0)).getExampleTable();
                Attribute centroidClusterAttribute = centroidTable.findAttribute("cluster");
                HashMap<Double,DataRow> centroidHm = new HashMap<>();
                // The hashmap contains the cluster's centroid's features data
                for (int j=0; j<centroidTable.size(); j++) {
                    centroidHm.put(centroidTable.getDataRow(j).get(centroidClusterAttribute),centroidTable.getDataRow(j));
                }

                ArrayList<Double> distances = new ArrayList<>();

                // For each file, calculate the distance with it's cluster's centroid
                ExampleTable dataTable = ((SimpleExampleSet) result.getElementAt(1)).getExampleTable();
                Attribute dataClusterAttribute = dataTable.findAttribute("cluster");
                for (int j=0; j<dataTable.size(); j++) {
                    double cluster = dataTable.getDataRow(j).get(dataClusterAttribute);
                    //TODO: check if it is always double[] data !!
                    // Get features as ArrayList
                    //TODO: use org.apache.commons.lang.ArrayUtils once amuse is switched to maven for better complexity
                    List<Double> centroidFeatures = Arrays.stream(((DoubleArrayDataRow) centroidHm.get(cluster)).getData()).boxed().collect(Collectors.toList());
                    List<Double> dataFeatures = Arrays.stream(((DoubleArrayDataRow) dataTable.getDataRow(j)).getData()).boxed().collect(Collectors.toList());
                    // Calculate Euclidian distance and save it to the results's list
                    //TODO: save the cluster number ?? arraylist -> hashmap with cluster as key and distances as value.
                    distances.add(euclidianDistance(centroidFeatures, dataFeatures));
                }

                //TODO: should be in own measure class
                Double meanDistances = distances.stream().mapToDouble(d -> d).average().orElse(0.0);
                //ValidationMeasureDouble[] meanClusterDistances = new ValidationMeasureDouble[1];
                ValidationMeasureDouble meanClusterDistances = new ValidationMeasureDouble();
                meanClusterDistances.setId(400);
                meanClusterDistances.setName("Mean cluster distance (Euclidian)");
                meanClusterDistances.setValue(meanDistances);

                ArrayList<ValidationMeasure> measureList = new ArrayList<>();
                measureList.add(meanClusterDistances);
                ((ValidationConfiguration)this.getCorrespondingScheduler().getConfiguration()).setCalculatedMeasures(measureList);

            } catch(Exception e) {
                throw new NodeException("Error classifying data: " + e.getMessage());
            }
            //END TODO
        }

    }

    /*
     * (non-Javadoc)
     * @see amuse.nodes.validator.interfaces.ValidatorInterface#calculateListOfUsedProcessedFeatureFiles()
     */
    public ArrayList<String> calculateListOfUsedProcessedFeatureFiles() throws NodeException {
        //TODO
        return null;
    }

    /*
     * Calculate Euclidian distance between two features ArrayList
     */
    private double euclidianDistance(List<Double> centroidFeatures, List<Double> dataFeatures) {
        double sum = 0.0;
        for (int k=0; k<centroidFeatures.size()-1; k++) { // size-1 because cluster number is in the features list
            sum+=Math.pow(centroidFeatures.get(k)-dataFeatures.get(k),2);
        }
        return Math.sqrt(sum);
    }

}
