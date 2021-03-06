package org.carlspring.strongbox.security.vote;

import java.util.Arrays;

import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.stereotype.Component;

/**
 * @author Alex Oreshkevich
 */
@Component
public class MethodAccessDecisionManager
        extends AffirmativeBased
        implements AccessDecisionManager
{

    @SuppressWarnings("unchecked")
    public MethodAccessDecisionManager()
    {
        super(Arrays.asList(new CustomAccessDecisionVoter(),
                            new CustomPreInvocationAuthorizationAdviceVoter(new ExpressionBasedPreInvocationAdvice()),
                            new RoleVoter(),
                            new AuthenticatedVoter()
        ));
    }
}
