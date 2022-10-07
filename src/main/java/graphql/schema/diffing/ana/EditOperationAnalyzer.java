package graphql.schema.diffing.ana;

import graphql.schema.GraphQLSchema;
import graphql.schema.diffing.Edge;
import graphql.schema.diffing.EditOperation;
import graphql.schema.diffing.SchemaGraph;
import graphql.schema.diffing.Vertex;
import graphql.schema.idl.ScalarInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static graphql.Assert.assertTrue;
import static graphql.schema.diffing.ana.SchemaChanges.*;

/**
 * Higher level GraphQL semantic assigned to
 */
public class EditOperationAnalyzer {

    private GraphQLSchema oldSchema;
    private GraphQLSchema newSchema;
    private SchemaGraph oldSchemaGraph;
    private SchemaGraph newSchemaGraph;

    private List<SchemaChange> changes = new ArrayList<>();
    private Map<String, ObjectChanged> objectChangedMap = new LinkedHashMap<>();
    private Map<String, InterfaceChanged> interfaceChangedMap = new LinkedHashMap<>();

    public EditOperationAnalyzer(GraphQLSchema oldSchema,
                                 GraphQLSchema newSchema,
                                 SchemaGraph oldSchemaGraph,
                                 SchemaGraph newSchemaGraph
    ) {
        this.oldSchema = oldSchema;
        this.newSchema = newSchema;
        this.oldSchemaGraph = oldSchemaGraph;
        this.newSchemaGraph = newSchemaGraph;
    }

    public List<SchemaChange> analyzeEdits(List<EditOperation> editOperations) {
        for (EditOperation editOperation : editOperations) {
            switch (editOperation.getOperation()) {
                case INSERT_VERTEX:
                    insertedVertex(editOperation);
                    break;
                case DELETE_VERTEX:
                    deletedVertex(editOperation);
                    break;
                case CHANGE_VERTEX:
                    changeVertex(editOperation);
                    break;
                case INSERT_EDGE:
                    insertedEdge(editOperation);
                    break;
                case DELETE_EDGE:
                    deletedEdge(editOperation);
                    break;
                case CHANGE_EDGE:
                    changedEdge(editOperation);
                    break;
            }
        }
        changes.addAll(objectChangedMap.values());
        changes.addAll(interfaceChangedMap.values());
        return changes;
    }

    private void insertedVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                addedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                addedInterface(editOperation);
                break;
            case SchemaGraph.UNION:
                addedUnion(editOperation);
                break;
            case SchemaGraph.INPUT_OBJECT:
                addedInputObject(editOperation);
                break;
            case SchemaGraph.ENUM:
                addedEnum(editOperation);
                break;
            case SchemaGraph.SCALAR:
                addedScalar(editOperation);
                break;
            case SchemaGraph.FIELD:
                addedField(editOperation);
                break;
            case SchemaGraph.INPUT_FIELD:
                addedInputField(editOperation);
                break;
        }

    }

    private void deletedVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                removedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                removedInterface(editOperation);
                break;
            case SchemaGraph.UNION:
                removedUnion(editOperation);
                break;
            case SchemaGraph.INPUT_OBJECT:
                removedInputObject(editOperation);
                break;
            case SchemaGraph.ENUM:
                removedEnum(editOperation);
                break;
            case SchemaGraph.SCALAR:
                removedScalar(editOperation);
                break;
        }
    }

    private void changeVertex(EditOperation editOperation) {
        switch (editOperation.getTargetVertex().getType()) {
            case SchemaGraph.OBJECT:
                changedObject(editOperation);
                break;
            case SchemaGraph.INTERFACE:
                changedInterface(editOperation);
                break;
            case SchemaGraph.UNION:
                changedUnion(editOperation);
                break;
            case SchemaGraph.INPUT_OBJECT:
                changedInputObject(editOperation);
                break;
            case SchemaGraph.ENUM:
                changedEnum(editOperation);
                break;
            case SchemaGraph.SCALAR:
                changedScalar(editOperation);
                break;
            case SchemaGraph.FIELD:
                changedField(editOperation);
                break;
            case SchemaGraph.INPUT_FIELD:
                changedInputField(editOperation);
                break;
        }

    }

    private void insertedEdge(EditOperation editOperation) {
        Edge newEdge = editOperation.getTargetEdge();
        Vertex one = newEdge.getOne();
        Vertex two = newEdge.getTwo();
        if (newEdge.getLabel().startsWith("implements ")) {
            Vertex objectVertex;
            Vertex interfaceVertex;
            if (one.isOfType(SchemaGraph.OBJECT) && two.isOfType(SchemaGraph.INTERFACE)) {
                objectVertex = newEdge.getOne();
                interfaceVertex = newEdge.getTwo();
                ObjectChanged.AddedInterfaceToObjectDetail addedInterfaceToObjectDetail = new ObjectChanged.AddedInterfaceToObjectDetail(interfaceVertex.getName());
                getObjectChanged(objectVertex.getName()).getObjectChangeDetails().add(addedInterfaceToObjectDetail);
            } else if (two.isOfType(SchemaGraph.INTERFACE) && one.isOfType(SchemaGraph.OBJECT)) {
                objectVertex = newEdge.getTwo();
                interfaceVertex = newEdge.getOne();
                ObjectChanged.AddedInterfaceToObjectDetail addedInterfaceToObjectDetail = new ObjectChanged.AddedInterfaceToObjectDetail(interfaceVertex.getName());
                getObjectChanged(objectVertex.getName()).getObjectChangeDetails().add(addedInterfaceToObjectDetail);
            }else{
                // this means we need to have an interface implementing another interface
            }
        }
    }

    private ObjectChanged getObjectChanged(String newName) {
        if (!objectChangedMap.containsKey(newName)) {
            objectChangedMap.put(newName, new ObjectChanged(newName));
        }
        return objectChangedMap.get(newName);
    }

    private InterfaceChanged getInterfaceChanged(String newName) {
        if (!interfaceChangedMap.containsKey(newName)) {
            interfaceChangedMap.put(newName, new InterfaceChanged(newName));
        }
        return interfaceChangedMap.get(newName);
    }

    private void deletedEdge(EditOperation editOperation) {

    }

    private void changedEdge(EditOperation editOperation) {

    }


    private void addedObject(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void addedInterface(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        InterfaceAdded interfacedAdded = new InterfaceAdded(objectName);
        changes.add(interfacedAdded);
    }

    private void addedUnion(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void addedInputObject(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void addedEnum(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void addedScalar(EditOperation editOperation) {
        String scalarName = editOperation.getTargetVertex().getName();
        // build in scalars can appear as added when not used in the old schema, but
        // we don't want to register them as new Scalars
        if (ScalarInfo.isGraphqlSpecifiedScalar(scalarName)) {
            return;
        }

        ScalarAdded scalarAdded = new ScalarAdded(scalarName);
        changes.add(scalarAdded);
    }

    private void addedField(EditOperation editOperation) {
        Vertex newField = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(newField);
        FieldAdded objectAdded = new FieldAdded(newField.getName(), fieldsContainerForField.getName());
        changes.add(objectAdded);
    }

    private void addedInputField(EditOperation editOperation) {
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void removedObject(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        changes.add(change);
    }

    private void removedInterface(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        changes.add(change);
    }

    private void removedUnion(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        changes.add(change);
    }

    private void removedInputObject(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        changes.add(change);
    }

    private void removedEnum(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        changes.add(change);
    }

    private void removedScalar(EditOperation editOperation) {
        String objectName = editOperation.getSourceVertex().getName();

        ObjectRemoved change = new ObjectRemoved(objectName);
        changes.add(change);
    }

    private void changedObject(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void changedInterface(EditOperation editOperation) {
        String interfaceName = editOperation.getTargetVertex().getName();
        InterfaceChanged interfaceChanged = getInterfaceChanged(interfaceName);

    }

    private void changedUnion(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void changedEnum(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void changedInputObject(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void changedScalar(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }

    private void changedField(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        Vertex field = editOperation.getTargetVertex();
        Vertex fieldsContainerForField = newSchemaGraph.getFieldsContainerForField(field);

        FieldChanged objectAdded = new FieldChanged(field.getName(), fieldsContainerForField.getName());
        changes.add(objectAdded);
    }

    private void changedInputField(EditOperation editOperation) {
        // object changes include: adding/removing Interface, adding/removing applied directives, changing name
        String objectName = editOperation.getTargetVertex().getName();

        ObjectAdded objectAdded = new ObjectAdded(objectName);
        changes.add(objectAdded);
    }


}
