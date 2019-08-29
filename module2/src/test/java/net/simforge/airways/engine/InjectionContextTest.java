/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.engine;

import org.junit.Test;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InjectionContextTest {
    @Test
    public void singleService() {
        OneService oneService = new OneService();

        RootObject rootObject = new RootObject();
        assertNull(rootObject.oneService);
        assertNull(rootObject.anotherService);

        InjectionContext.create()
                .add(OneService.class, oneService)
                .inject(rootObject);
        assertEquals(oneService, rootObject.oneService);
        assertNull(rootObject.anotherService);
    }

    @Test
    public void twoServices() {
        OneService oneService = new OneService();
        AnotherService anotherService = new AnotherService();

        RootObject rootObject = new RootObject();
        assertNull(rootObject.oneService);
        assertNull(rootObject.anotherService);

        InjectionContext.create()
                .add(OneService.class, oneService)
                .add(AnotherService.class, anotherService)
                .inject(rootObject);
        assertEquals(oneService, rootObject.oneService);
        assertEquals(anotherService, rootObject.anotherService);
    }

    @Test
    public void inheritedFields() {
        OneService oneService = new OneService();
        AnotherService anotherService = new AnotherService();

        Subclass subclass = new Subclass();
        assertNull(subclass.oneService);
        assertNull(subclass.anotherService);
        assertNull(subclass.oneMoreOneService);

        InjectionContext.create()
                .add(OneService.class, oneService)
                .add(AnotherService.class, anotherService)
                .inject(subclass);
        assertEquals(oneService, subclass.oneService);
        assertEquals(anotherService, subclass.anotherService);
        assertEquals(oneService, subclass.oneMoreOneService);

    }

    @Test
    public void interfacesSupport() {
        OneServiceSubclass oneServiceSubclass = new OneServiceSubclass();

        RootObject rootObject = new RootObject();
        assertNull(rootObject.oneService);

        InjectionContext.create()
                .add(OneServiceSubclass.class, oneServiceSubclass)
                .inject(rootObject);
        assertEquals(oneServiceSubclass, rootObject.oneService);
    }

    // todo recursive injection to deeper level of objects
    // todo prevention of circular links

    static class RootObject {
        @Inject
        OneService oneService;
        @Inject
        AnotherService anotherService;
    }

    static class Subclass extends RootObject {
        @Inject
        OneService oneMoreOneService;
    }

    static class OneService {
    }

    static class AnotherService {
    }

    static class OneServiceSubclass extends OneService {

    }
}
