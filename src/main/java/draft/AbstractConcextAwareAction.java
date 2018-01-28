package draft;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

/**
 * Created by Pavel Kaplya on 28.01.2018.
 */
public abstract class AbstractConcextAwareAction<O,  S extends Enum<S>, E extends Enum<E>> implements Action<S, E> {
    private AbstractContextAwareEnumStateMachineService<O, S, E> handler;

    @Autowired
    public void setHandler(AbstractContextAwareEnumStateMachineService<O, S, E> handler) {
        this.handler = handler;
    }

    @Override
    public final void execute(StateContext<S, E> context) {
        O object = handler.getManagedObjectByStateMachine(context.getStateMachine());
        doExecute(object, context);
    }

    public abstract void doExecute(O object, StateContext<S, E> context);
}
