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
 * O - Managed object type
 * S - states enum
 * E - events enum
 */
public abstract class AbstractContextAwareEnumStateMachineService<O, S extends Enum<S>, E extends Enum<E>> extends LifecycleObjectSupport {

    @Autowired
    private StateMachineFactory<S, E> stateMachineFactory;

    private final Field stateField;
    private final Class stateEnumClass;

    /**
     * Two maps containing links from object to SM and back.
     * I'm concerned that equal method can lead to single SM associated with "diffferent" but equal objects
     */
    private final LoadingCache<O, StateMachine<S, E>> managedObjectsSMCache = configureCache().build(cacheLoader());
    private final Map<StateMachine<S, E>, O> invertedMap = new ConcurrentHashMap<>();

    /**
     * protected predefined configuration for the cache
     */
    protected CacheBuilder<Object, Object> configureCache() {
        return CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .removalListener(t-> invertedMap.remove(t.getValue()))
                .maximumSize(100L);
    }

    /**
     * Loader that is invoked if the entry is missing in the cache for the requested key
     */
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

                stateMachine.addStateListener(new ObjectStateChangeListenerAdapter(key));

                stateMachine.start();
                invertedMap.put(stateMachine, key);
                return stateMachine;
            }
        };
    }

    public AbstractContextAwareEnumStateMachineService() {
        super();

        Class objectClazz = (Class) (((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0]);
        stateEnumClass = (Class) (((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1]);

        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(objectClazz, StateField.class);

        if (fields.size() > 1)
            throw new RuntimeException("Too many StateField fields in the object " + objectClazz.getName());
        else if (fields.isEmpty())
            throw new RuntimeException("No StateField field found in the object " + objectClazz.getName());
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

    /**
     * public service procedure to be called from the outside
     * @param object
     * @param event
     * @return
     */
    public boolean sendEvent(O object, E event) {
        StateMachine<S, E> stateMachine = managedObjectsSMCache.getUnchecked(object);

        try {
            if (getState(object) != null && stateMachine.getState().getId() != getState(object))
                throw new RuntimeException("Object state mismatch");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Message<E> message = MessageBuilder
                .withPayload(event)
                .build();
        return stateMachine.sendEvent(message);
    }

    /**
     * method that is used by abstract methods(Actions/Guards) to get the managed object by the SM
     * @param stateMachine
     * @return
     */
    O getManagedObjectByStateMachine(StateMachine<S, E> stateMachine) {
        return invertedMap.get(stateMachine);
    }

    /**
     * Listener that executes the state change of the managed object
     */
    private class ObjectStateChangeListenerAdapter extends StateMachineListenerAdapter<S, E> {

        private final O obj;

        ObjectStateChangeListenerAdapter(O obj) {
            this.obj = obj;
        }

        @Override
        public void transition(Transition<S, E> transition) {
            setState(obj, transition.getTarget().getId());
        }
    }


}
