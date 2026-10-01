package dev.playground.library.openlibrary;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link OpenLibraryApi} as a bean: a proxy backed by a {@code RestClient} of its own.
 * The interfaces of one group share one client, and Spring Boot configures that client from
 * {@code spring.http.serviceclient.<group>.*}: base URL, connect and read timeouts (see
 * application.yml). Without a read timeout, a server that accepts the connection and then never
 * answers would hold the request thread forever. Guide: §5.9 Beyond CRUD.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "openlibrary", types = OpenLibraryApi.class)
public class OpenLibraryConfig {}
