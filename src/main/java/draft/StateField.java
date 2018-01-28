package draft;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by Pavel Kaplya on 18.01.2018.
 * Annotated field is treated as the State field of the object.
 * Spring statemachine changes its value if the object is managed by it
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StateField {
}
