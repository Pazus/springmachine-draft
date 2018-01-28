package draft;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import draft.PersistEnumStateMachineHandlerTest.TestPersistEnumStateMachineHandler;
import draft.PersistEnumStateMachineHandlerTest.TestStateMachineAdapter;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Created by Pavel Kaplya on 19.01.2018.
 */
@SpringJUnitConfig(classes = {TestPersistEnumStateMachineHandler.class, TestStateMachineAdapter.class})
class PersistEnumStateMachineHandlerTest {

    @Autowired
    private TestPersistEnumStateMachineHandler fsmService;

    @Test
    void test() {
        assertNotNull(fsmService);

        TestEntity testEntity = new TestEntity();

        fsmService.sendEvent(testEntity, Events.E1);

        assertEquals(States.S2, testEntity.getState());
    }

    enum States {
        S1, S2
    }

    enum Events {
        E1, E2
    }

    @Service
    static class TestPersistEnumStateMachineHandler extends PersistEnumStateMachineHandler<TestEntity, States, Events> {

    }

    @Configuration
    @EnableStateMachineFactory
    static class TestStateMachineAdapter extends EnumStateMachineConfigurerAdapter<States, Events> {
        @Override
        public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
            states.withStates()
                    .initial(States.S1)
                    .end(States.S2)
                    .states(EnumSet.allOf(States.class));
        }

        @Override
        public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {

            transitions.withExternal()
                    .source(States.S1).target(States.S2).event(Events.E1)
                    .and().withExternal()
                    .source(States.S2).target(States.S1).event(Events.E2);
        }

    }

    static class TestEntity {
        private Long id;

        @StateField
        private States state;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public States getState() {
            return state;
        }

        public void setState(States state) {
            this.state = state;
        }
    }

}