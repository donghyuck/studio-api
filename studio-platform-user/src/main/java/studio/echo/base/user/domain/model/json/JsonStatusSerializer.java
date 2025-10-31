/**
 *
 * @author  donghyuck, son
 * @since 2025-10-14
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-10-14  donghyuck, son: 최초 생성.
 * </pre>
 */

package studio.echo.base.user.domain.model.json;

import studio.echo.base.user.domain.model.Status;

/**
 *
 * @author  donghyuck, son
 * @since 2025-10-14
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-10-14  donghyuck, son: 최초 생성.
 * </pre>
 */

public class JsonStatusSerializer extends com.fasterxml.jackson.databind.JsonSerializer<Status> {

    @Override
    public void serialize(Status value,
            com.fasterxml.jackson.core.JsonGenerator gen,
            com.fasterxml.jackson.databind.SerializerProvider serializers)
            throws java.io.IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(value.toJson());
    }
}
