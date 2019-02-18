package amuse.nodes.classifier.methods.unsupervised;

import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierInterface;
import amuse.util.LibraryInitializer;

import com.rapidminer.Process;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.Ontology;
import com.rapidminer.tools.OperatorService;

public class RapidMinerUnsupervisedModelLoader extends AmuseTask implements ClassifierInterface {

    /*
     * (non-Javadoc)
     * @see amuse.interfaces.AmuseTaskInterface#setParameters(java.lang.String)
     */
    public void setParameters(String parameterString) {
        // Does nothing
    }

    /*
     * (non-Javadoc)
     * @see amuse.interfaces.AmuseTaskInterface#initialize()
     */
    public void initialize() throws NodeException {
        try {
            LibraryInitializer.initializeRapidMiner();
        } catch (Exception e) {
            throw new NodeException("Could not initialize RapidMiner: " + e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see amuse.nodes.classifier.interfaces.ClassifierInterface#classify(java.lang.String, java.util.ArrayList, java.lang.String)
     */
    public void classify(String pathToModelFile) throws NodeException {
        // Does nothing
    }

    // Triggered by extended class (cf. example SimpleKMeansAdapter)
    public void classify(Operator modelLoader) throws NodeException {
        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.
                getConfiguration()).getInputToClassify()).getDataSet();

        try {
            Process process = new Process();

            // (1) Create ExampleSet from the ClassificationConfiguration
            ExampleSet exampleSet = dataSetToClassify.convertToRapidMinerExampleSet();

            // add ID attribute to data set
            Attribute idAttribute = AttributeFactory.createAttribute("Id", Ontology.INTEGER);
            int dataRowSize = exampleSet.getExampleTable().size();
            exampleSet.getExampleTable().addAttribute(idAttribute);
            exampleSet.getAttributes().setId(idAttribute);
            for (int i = 0; i<dataRowSize;i++) {
                exampleSet.getExample(i).setId(i);
            }

            // (2) Load the model
            //Operator modelLoader = OperatorService.createOperator(algorithmName); //weka:W-SimpleKMeans W-FarthestFirst

            process.getRootOperator().getSubprocess(0).addOperator(modelLoader);

            // (3) Apply the model
            // model applier is not needed as 'example set' is transformed through clustering into 'clustered set'

            // (4) Connect the ports
            InputPort modelLoaderInputPort = modelLoader.getInputPorts().getPortByName("example set");
            //OutputPort modelLoaderOutputPort = modelLoader.getOutputPorts().getPortByName("cluster model");
            //OutputPort modelLoaderSetOutputPort = modelLoader.getOutputPorts().getPortByName("clustered set");
            OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);

            processOutputPort.connectTo(modelLoaderInputPort);

            // (5) Run the process
            process.run(new IOContainer(exampleSet));

            // (6) Convert the results to AMUSE EditableDataSet
            exampleSet.getAttributes().getCluster().setName("PredictedCategory");
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(
                    new DataSet(exampleSet)));

        } catch(Exception e) {
            throw new NodeException("Error classifying data: " + e.getMessage());
        }
    }

}
