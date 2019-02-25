package amuse.nodes.trainer.methods.unsupervised;

import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.trainer.TrainingConfiguration;
import amuse.nodes.trainer.interfaces.TrainerInterface;
import amuse.util.LibraryInitializer;
import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.KMedoids;
import com.rapidminer.operator.io.ClusterModelWriter;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.OperatorService;

public class KMedoidsAdapter extends AmuseTask implements TrainerInterface {

    private Integer cluster_number;

    public void setParameters(String parameterString) {
        // Default parameters
        if(parameterString == "" || parameterString == null) {
            cluster_number = 2;
        } else {
            cluster_number = new Integer(parameterString);
        }
    }

    public void initialize() throws NodeException {
        try {
            LibraryInitializer.initializeRapidMiner();
        } catch (Exception e) {
            throw new NodeException("Could not initialize RapidMiner: " + e.getMessage());
        }

    }

    public void trainModel(String outputModel) throws NodeException {
        DataSet dataSet = ((DataSetInput)((TrainingConfiguration)this.correspondingScheduler.getConfiguration()).getGroundTruthSource()).getDataSet();

        // Train the model and save it
        try {
            Process process = new Process();

            // Train the model
            Operator modelLearner = OperatorService.createOperator(KMedoids.class);//"weka:W-SimpleKMeans");

            // Set the parameters
            modelLearner.setParameter("k", "" + this.cluster_number);

            process.getRootOperator().getSubprocess(0).addOperator(modelLearner);

            // Write the model
            Operator modelWriter = OperatorService.createOperator(ClusterModelWriter.class);
            modelWriter.setParameter("cluster_model_file", outputModel);
            process.getRootOperator().getSubprocess(0).addOperator(modelWriter);

            // Connect the Ports
            InputPort modelLearnerInputPort = modelLearner.getInputPorts().getPortByName("example set");
            OutputPort modelLearnerOutputPort = modelLearner.getOutputPorts().getPortByName("cluster model");
            InputPort modelWriterInputPort = modelWriter.getInputPorts().getPortByName("input");
            OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);

            modelLearnerOutputPort.connectTo(modelWriterInputPort);
            processOutputPort.connectTo(modelLearnerInputPort);

            ExampleSet exampleSet = dataSet.convertToRapidMinerExampleSet();

            // Run the process
            process.run(new IOContainer(exampleSet));

        } catch (Exception e) {
            throw new NodeException("Classification training failed: " + e.getMessage());
        }
    }
}
