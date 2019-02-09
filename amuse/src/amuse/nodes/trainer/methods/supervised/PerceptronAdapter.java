package amuse.nodes.trainer.methods.supervised;

import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.trainer.TrainingConfiguration;
import amuse.nodes.trainer.interfaces.TrainerInterface;
import amuse.util.LibraryInitializer;
import com.rapidminer.Process;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.io.ModelWriter;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.OperatorService;

public class PerceptronAdapter extends AmuseTask implements TrainerInterface {

    private double learning_rate;

    public void setParameters(String parameterString) {
        // Default parameters
        if(parameterString == "" || parameterString == null) {
            learning_rate = 0.3;
        } else {
            learning_rate = new Double(parameterString);
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
            //Operator modelLearner = OperatorService.createOperator(Perceptron.class);
            Operator modelLearner = OperatorService.createOperator("weka:W-MultilayerPerceptron");

            // Set the parameters
            /*modelLearner.setParameter("rounds", new Integer(this.rounds).toString());
            modelLearner.setParameter("learning_rate", new Double(this.learning_rate).toString());*/
            modelLearner.setParameter("L", "" + this.learning_rate);

            process.getRootOperator().getSubprocess(0).addOperator(modelLearner);

            // Write the model
            Operator modelWriter = OperatorService.createOperator(ModelWriter.class);
            modelWriter.setParameter("model_file", outputModel);
            process.getRootOperator().getSubprocess(0).addOperator(modelWriter);

            // Connect the Ports
            InputPort modelLearnerInputPort = modelLearner.getInputPorts().getPortByName("training set");
            OutputPort modelLearnerOutputPort = modelLearner.getOutputPorts().getPortByName("model");
            InputPort modelWriterInputPort = modelWriter.getInputPorts().getPortByName("input");
            OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);

            modelLearnerOutputPort.connectTo(modelWriterInputPort);
            processOutputPort.connectTo(modelLearnerInputPort);

            // Run the process
            // TODO: debug point
            process.run(new IOContainer(dataSet.convertToRapidMinerExampleSet()));

        } catch (Exception e) {
            throw new NodeException("Classification training failed: " + e.getMessage());
        }
    }
}
