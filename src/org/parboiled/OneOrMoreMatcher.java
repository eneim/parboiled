package org.parboiled;

import org.jetbrains.annotations.NotNull;
import org.parboiled.support.Checks;
import org.parboiled.support.InputLocation;

import java.util.Set;

class OneOrMoreMatcher extends AbstractMatcher implements FollowMatcher {

    public OneOrMoreMatcher(@NotNull Rule subRule) {
        super(subRule);
    }

    public boolean match(@NotNull MatcherContext context, boolean enforced) {
        Matcher matcher = getChildren().get(0);

        boolean matched = context.runMatcher(matcher, enforced);
        if (!matched) return false;

        // collect all further matches as well
        InputLocation lastLocation = context.getCurrentLocation();
        while (context.runMatcher(matcher, false)) {
            InputLocation currentLocation = context.getCurrentLocation();
            Checks.ensure(currentLocation.index > lastLocation.index,
                    "The inner rule of OneOrMore rule '%s' must not allow empty matches", context.getPath());
            lastLocation = currentLocation;
        }

        context.createNode();
        return true;
    }

    public boolean collectFirstCharSet(@NotNull Set<Character> firstCharSet) {
        Checks.ensure(getChildren().get(0).collectFirstCharSet(firstCharSet),
                "Sub rule of an OneOrMore-rule must not allow empty matches");
        return true;
    }

    public boolean collectCurrentFollowerSet(MatcherContext context, @NotNull Set<Character> followerSet) {
        // since this call is only legal when we are currently within a match of our sub matcher,
        // i.e. the submatcher can either match once more or the repetition can legally terminate which means
        // our follower set addition is incomplete -> return false
        collectFirstCharSet(followerSet);
        return false;
    }
}
