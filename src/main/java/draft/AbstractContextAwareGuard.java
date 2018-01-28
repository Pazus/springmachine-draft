package draft;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;

/**
 * Created by Pavel Kaplya on 28.01.2018.
 */
public abstract class AbstractContextAwareGuard<O, S extends Enum<S>, E extends Enum<E>> implements Guard<S, E> {
    private PersistEnumStateMachineHandler<O, S, E> handler;

    @Autowired
    public void setHandler(PersistEnumStateMachineHandler<O, S, E> handler) {
        this.handler = handler;
    }

    @Override
    public final boolean evaluate(StateContext<S, E> context) {
        O object = handler.getEntityByStateMachine(context.getStateMachine());
        return doEvaluate(object, context);
    }

    abstract boolean doEvaluate(O object, StateContext<S, E> context);

}
