package studio.one.platform.ai.service.visualization;

import studio.one.platform.ai.core.vector.visualization.VectorProjection;

public interface VectorProjectionNotifier {

    VectorProjectionNotifier NOOP = projection -> {
    };

    void notifyProjection(VectorProjection projection);
}
