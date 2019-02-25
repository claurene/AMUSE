package amuse.nodes.classifier.methods.unsupervised;

import amuse.interfaces.nodes.NodeException;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.KMedoids;
import com.rapidminer.tools.OperatorService;

public class KMedoidsAdapter extends RapidMinerUnsupervisedModelLoader {

    private Integer cluster_number;

    @Override
    public void setParameters(String parameterString) {
        if(parameterString == "" || parameterString == null) {
            cluster_number = 2;
        } else {
            cluster_number = new Integer(parameterString);
        }
    }

    @Override
    public void classify(String pathToModelFile) throws NodeException {
        try {
            Operator modelLoader = OperatorService.createOperator(KMedoids.class);//"weka:W-SimpleKMeans");
            // Set parameters
            modelLoader.setParameter("k", "" + this.cluster_number); //N for weka k-means
            // Classify with this model
            super.classify(modelLoader);
        } catch (Exception e) {
            throw new NodeException("Error classifying data: " + e.getMessage());
        }
    }
}
