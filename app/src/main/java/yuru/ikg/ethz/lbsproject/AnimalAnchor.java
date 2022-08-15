package yuru.ikg.ethz.lbsproject;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;

import java.util.Date;

public class AnimalAnchor {
    private static final String TAG = AnimalAnchor.class.getSimpleName();

    private final String animal;

    private final AnchorNode anchorNode;
    private final int number;
    private TransformableNode transformableNode;
    private CloudSpatialAnchor cloudAnchor;
    private String anchorId;

    private Renderable gltfModel;
    private float scale;
    private ViewRenderable viewRenderable;
    private Activity MainThreadContext;

    public AnimalAnchor(String animal, int number, ArFragment arFragment, Anchor localAnchor) {
        this.animal = animal;
        this.number = number;

        anchorNode = new AnchorNode(localAnchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        transformableNode.getScaleController().setEnabled(true);
        transformableNode.setParent(this.anchorNode);
    }

    public void setModel(Renderable gltfModel, float scale) {
        Log.d(TAG, "setModel()");
        this.gltfModel = gltfModel;
        this.scale = scale;
    }

    public void setViewRenderable(ViewRenderable viewRenderable) {
        this.viewRenderable = viewRenderable;
    }

    public void render(ArFragment arFragment) {
        Log.d(TAG, "render()");
        assert (null != gltfModel);


        TransformableNode model = new TransformableNode(arFragment.getTransformationSystem());
        model.getScaleController().setMaxScale(scale);
        model.getScaleController().setMinScale(scale * 0.8f);

        model.setParent(anchorNode);
        model.setRenderable(gltfModel);
        model.getRenderableInstance().animate(true).start();
        model.select();

         // label
        Node titleNode = new Node();
        TextView titleText = viewRenderable.getView().findViewById(R.id.view_card_title);
        titleText.setText(animal + "(" + number + ")");
        titleNode.setParent(model);
        titleNode.setEnabled(false);
        titleNode.setLocalScale(new Vector3(1.0f / scale, 1.0f / scale, 1.0f / scale));
        titleNode.setLocalPosition(new Vector3(0.1f / scale , 0.4f / scale , 0.1f));
        titleNode.setRenderable(viewRenderable);
        titleNode.setEnabled(true);

    }

    public void addCloudAnchor(Date expireDate) {
        Log.d(TAG, "addCloudAnchor()");
        this.cloudAnchor = new CloudSpatialAnchor();
        this.cloudAnchor.setLocalAnchor(this.getLocalAnchor());
        this.cloudAnchor.setExpiration(expireDate);

    }


    public AnchorNode getAnchorNode() {
        return anchorNode;
    }

    public TransformableNode getTransformableNode() {
        return transformableNode;
    }

    public void setTransformableNode(TransformableNode transformableNode) {
        this.transformableNode = transformableNode;
    }

    public CloudSpatialAnchor getCloudAnchor() {
        return cloudAnchor;
    }

    public void setCloudAnchor(CloudSpatialAnchor cloudAnchor) {
        this.cloudAnchor = cloudAnchor;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(String anchorId) {
        this.anchorId = anchorId;
    }

    public String getAnimal() {
        return animal;
    }

    public int getNumber() {
        return number;
    }

    public Anchor getLocalAnchor() {
        return this.anchorNode.getAnchor();
    }

    public void destroy() {
        MainThreadContext.runOnUiThread(() -> {
            anchorNode.setRenderable(null);
            anchorNode.setParent(null);
            Anchor localAnchor =  anchorNode.getAnchor();
            if (localAnchor != null) {
                anchorNode.setAnchor(null);
                localAnchor.detach();
            }
        });
    }
}
