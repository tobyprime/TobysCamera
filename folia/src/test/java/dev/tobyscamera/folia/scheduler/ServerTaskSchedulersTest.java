package dev.tobyscamera.folia.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerTaskSchedulersTest {
    @Test
    void recognizesTheFoliaRuntimeMarkerRegardlessOfServerBrand() {
        ClassLoader markerLoader = new ClassLoader(null) {
            @Override
            public java.net.URL getResource(String name) {
                return name.equals("io/papermc/paper/threadedregions/RegionizedServer.class")
                        ? ServerTaskSchedulersTest.class.getResource("ServerTaskSchedulersTest.class")
                        : null;
            }
        };

        assertTrue(ServerTaskSchedulers.isFolia(markerLoader));
    }

    @Test
    void treatsAMarkerlessRuntimeAsPaper() {
        assertFalse(ServerTaskSchedulers.isFolia(new ClassLoader(null) { }));
    }
}
