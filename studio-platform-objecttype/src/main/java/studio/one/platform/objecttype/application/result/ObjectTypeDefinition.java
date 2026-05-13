package studio.one.platform.objecttype.application.result;

public class ObjectTypeDefinition {

    private final ObjectTypeView type;
    private final ObjectTypePolicyView policy;

    public ObjectTypeDefinition(ObjectTypeView type, ObjectTypePolicyView policy) {
        this.type = type; this.policy = policy;
    }

    public ObjectTypeView type() { return type; }
    public ObjectTypePolicyView policy() { return policy; }
}
