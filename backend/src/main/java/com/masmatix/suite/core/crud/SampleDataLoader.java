package com.masmatix.suite.core.crud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masmatix.suite.core.security.AppUser;
import com.masmatix.suite.core.security.Role;
import com.masmatix.suite.core.security.UserRepository;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads demo users and the invented sample rows in classpath:seed/*.json
 * into empty tables. Nothing here is real customer or client data.
 */
@Component
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private final ApplicationContext context;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean enabled;
    private final String demoPassword;

    public SampleDataLoader(ApplicationContext context, ObjectMapper mapper, UserRepository users,
                            PasswordEncoder encoder,
                            @Value("${app.seed.enabled:true}") boolean enabled,
                            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.context = context;
        this.mapper = mapper;
        this.users = users;
        this.encoder = encoder;
        this.enabled = enabled;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            return;
        }
        if (users.count() == 0) {
            if (demoPassword == null || demoPassword.length() < 8) {
                throw new IllegalStateException("Set APP_DEMO_PASSWORD (8+ characters) to create the demo users");
            }
            for (Role r : Role.values()) {
                users.save(new AppUser(r.name().toLowerCase(), encoder.encode(demoPassword), r));
            }
            log.info("Created demo users admin, manager and staff");
        }

        Repositories repos = new Repositories(context);
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:seed/*.json");
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                JsonNode root = mapper.readTree(in);
                Class<?> type = Class.forName(root.get("entity").asText());
                @SuppressWarnings("unchecked")
                CrudRepository<Object, Object> repo = (CrudRepository<Object, Object>) repos
                        .getRepositoryFor(type)
                        .orElseThrow(() -> new IllegalStateException("No repository for " + type));
                if (repo.count() > 0) {
                    continue;
                }
                int n = 0;
                for (JsonNode row : root.get("rows")) {
                    repo.save(mapper.treeToValue(row, type));
                    n++;
                }
                log.info("Loaded {} sample rows into {}", n, type.getSimpleName());
            }
        }
    }
}
