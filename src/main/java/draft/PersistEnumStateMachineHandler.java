package draft;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.access.StateMachineAccess;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.statemachine.support.LifecycleObjectSupport;
import org.springframework.statemachine.transition.Transition;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Pavel Kaplya on 17.01.2018.
 */
public abstract class PersistEnumStateMachineHandler<O, S extends Enum<S>, E extends Enum<E>> extends LifecycleObjectSupport {

    public static final String OBJ_REFERENCE_HEADER_NAME = "OBJ_REFERENCE_HEADER_NAME";

    @Autowired
    private StateMachineFactory<S, E> stateMachineFactory;

    private final Field stateField;
    private final Class stateEnumClass;

    private LoadingCache<O, StateMachine<S, E>> entityCache = configureCache().build(cacheLoader());
    private Map<StateMachine<S, E>, O> invertedMap = new ConcurrentHashMap<>();

    protected CacheBuilder<Object, Object> configureCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .removalListener(t-> invertedMap.remove(t.getValue()))
                .maximumSize(100L);
    }

    private CacheLoader<O, StateMachine<S, E>> cacheLoader() {
        return new CacheLoader<O, StateMachine<S, E>>() {
            @Override
            public StateMachine<S, E> load(@Nonnull O key) throws Exception {
                StateMachine<S, E> stateMachine = stateMachineFactory.getStateMachine(UUID.randomUUID());
                S state = getState(key);

                List<StateMachineAccess<S, E>> withAllRegions = stateMachine.getStateMachineAccessor().withAllRegions();
                for (StateMachineAccess<S, E> a : withAllRegions) {
                    a.resetStateMachine(new DefaultStateMachineContext<>(state, null, null, null));
                }

                stateMachine.addStateListener(new EntityStateChangeStateMachineListenerAdapter(key));

                stateMachine.start();
                invertedMap.put(stateMachine, key);
                return stateMachine;
            }
        };
    }

    public PersistEnumStateMachineHandler() {
        super();

        Class entityClazz = (Class) (((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        stateEnumClass = (Class) (((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1]);

        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(entityClazz/*.getClass()*/, StateField.class);

        if (fields.size() > 1)
            throw new RuntimeException("Too many StateField fields in the entity " + entityClazz.getClass().getName());
        else if (fields.isEmpty())
            throw new RuntimeException("No StateField field found in the entity " + entityClazz.getClass().getName());
        stateField = fields.get(0);

        if (stateField.getType() != stateEnumClass)
            throw new RuntimeException("State field class mismatch");
        if (Modifier.isFinal(stateField.getModifiers()))
            throw new RuntimeException("State field is final");

        stateField.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    protected S getState(O e) throws IllegalAccessException {
        return (S) stateField.get(e);
    }

    protected void setState(O e, S state) {
        try {
            stateField.set(e, state);
        } catch (IllegalAccessException e1) {
            throw new RuntimeException(e1);
        }
    }

    public boolean sendEvent(O entity, E event) {
        StateMachine<S, E> stateMachine = entityCache.getUnchecked(entity);

        try {
            if (getState(entity) != null && stateMachine.getState().getId() != getState(entity))
                throw new RuntimeException("Entity state mismatch");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Message<E> message = MessageBuilder
                .withPayload(event)
                .setHeader(OBJ_REFERENCE_HEADER_NAME, entity)
                .build();
        return stateMachine.sendEvent(message);
    }

    public O getEntityByStateMachine(StateMachine<S, E> stateMachine) {
        return invertedMap.get(stateMachine);
    }

    private class EntityStateChangeStateMachineListenerAdapter extends StateMachineListenerAdapter<S, E> {

        private final O obj;

        EntityStateChangeStateMachineListenerAdapter(O obj) {
            this.obj = obj;
        }

        @Override
        public void transition(Transition<S, E> transition) {
            setState(obj, transition.getTarget().getId());
        }
    }


}
