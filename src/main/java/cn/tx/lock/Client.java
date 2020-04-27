package cn.tx.lock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Client {
    public static void main(String[] args) throws Exception {
        DistributeLock lock = new DistributeLock();

        Path path = Paths.get("d:\\test.txt");
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        ExecutorService service = Executors.newCachedThreadPool();
        for (int i = 0; i < 500; i++) {
            service.submit(() -> {
                DistributeLock.Node node = lock.lock();
                //获得文件的第一行
                try {
                    String numStr = Files.lines(path).findFirst().orElse("0");
                    int count = Integer.parseInt(numStr);
                    count++;
                    Files.write(path, new String(count + "").getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    //解锁
                    lock.unlock(node);
                }
            });
        }

        service.shutdown();
        service.awaitTermination(10, TimeUnit.SECONDS);
        String numStr = Files.lines(path).findFirst().orElse("0");
        System.out.println("总票数：" + numStr);

        System.out.println("-------------------------");
        System.out.println("test");
    }
}
