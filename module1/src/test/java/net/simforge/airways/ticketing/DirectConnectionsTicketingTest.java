/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ticketing;

import net.simforge.airways.persistence.Airways;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import org.hibernate.SessionFactory;
import org.junit.*;

import static junit.framework.TestCase.fail;

// todo p1 write simple test
public class DirectConnectionsTicketingTest {

    protected static SessionFactory sessionFactory;

    @BeforeClass
    public static void beforeClass() {
        sessionFactory = SessionFactoryBuilder
                .forDatabase("test")
                .createSchemaIfNeeded()
                .entities(Airways.entities)
                .build();
    }

    @AfterClass
    public static void afterClass() {
        sessionFactory.close();
        sessionFactory = null;
    }

    @Before
    public void before() {
        buildWorld();
    }

    @After
    public void after() {
    }





    protected void buildWorld() {

    }

    @Test
    public void test_ok() {
        DirectConnectionsTicketing.search(null);
    }

    @Test
    public void test_ok2() {
        DirectConnectionsTicketing.search(null);
    }

    @Test
    public void test_noRoute() {
        fail();
    }
}
