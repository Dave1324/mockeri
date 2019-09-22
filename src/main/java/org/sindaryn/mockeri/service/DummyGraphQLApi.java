package org.sindaryn.mockeri.service;

import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import org.springframework.stereotype.Service;

@Service
@GraphQLApi
public class DummyGraphQLApi {
    @GraphQLQuery(name = "dummyGraphQLEndpoint")
    public String hello(){
        return "This endpoint exists to absolve applications wishing to use " +
                "just mockeri from having to comply with the apifi dependency requirement of" +
                "at least one graphql resolver exposed. The running assumption is that mockeri" +
                "will only ever be used in test or dev environments, thereby negating any potential" +
                "side effects of having a potentially unwanted /graphql POST endpoint open to the web." +
                "Always make sure to keep mockeri in 'test' scope or similar.";
    }
}
