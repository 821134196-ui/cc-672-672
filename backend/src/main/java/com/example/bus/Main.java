package com.example.bus;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.nio.file.Files;
import java.nio.file.Path;

/** 应用入口：启动前确保 SQLite 数据目录存在（SQLite 驱动不会自动建目录，且 DDL 早于 StartupEvent） */
@QuarkusMain
public class Main {
    public static void main(String... args) throws Exception {
        Files.createDirectories(Path.of("data"));
        Quarkus.run(args);
    }
}
