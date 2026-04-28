package studio.one.application.attachment.autoconfigure.condition;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
@Conditional(OnAttachmentThumbnailEnabledCondition.class)
public @interface ConditionalOnAttachmentThumbnailEnabled {
}
