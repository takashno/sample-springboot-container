package com.zomu_t.sample;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(path = "/oom")
@Slf4j
public class OomController {

    @GetMapping(path = "simple")
    public ResponseEntity occurred_oom_simple() {
        try {
            new ArrayList(Integer.MAX_VALUE);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "process")
    public ResponseEntity occurred_oom_process() throws InterruptedException {
        try {
            List<byte[]> list = new ArrayList<>();
            int index = 1;

            while (true) {
                Thread.sleep(200);
                // 1MB each loop, 1 x 1024 x 1024 = 1048576
                byte[] b = new byte[1048576];
                list.add(b);
                Runtime rt = Runtime.getRuntime();
                System.out.printf("[%d] free memory: %s%n", index++, rt.freeMemory());
            }
        } catch (OutOfMemoryError e) {
            log.error("oom occurred...", e);
        }
        return ResponseEntity.ok().body("process end.");
    }

}
