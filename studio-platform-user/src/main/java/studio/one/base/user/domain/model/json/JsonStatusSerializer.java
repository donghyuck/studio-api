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

package studio.one.base.user.domain.model.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import studio.one.base.user.domain.model.Status;

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

public class JsonStatusSerializer extends ValueSerializer<Status> {

    @Override
    public void serialize(Status value,
            JsonGenerator generator,
            SerializationContext context) {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeString(value.toJson());
    }
}
