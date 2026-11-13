package ctbrec.event;

import ctbrec.event.EventHandlerConfiguration.PredicateConfiguration;

public class MatchAllPredicate extends EventPredicate {

    @Override
    public boolean test(Event evt) {
        return true;
    }

    @Override
    public void configure(PredicateConfiguration pc) {
        // noop
    }

}
