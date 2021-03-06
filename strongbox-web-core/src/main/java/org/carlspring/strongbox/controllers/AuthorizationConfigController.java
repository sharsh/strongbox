package org.carlspring.strongbox.controllers;

import org.carlspring.strongbox.security.Privilege;
import org.carlspring.strongbox.security.Role;
import org.carlspring.strongbox.users.domain.User;
import org.carlspring.strongbox.users.security.AuthorizationConfig;
import org.carlspring.strongbox.users.security.AuthorizationConfigProvider;
import org.carlspring.strongbox.users.service.UserService;
import org.carlspring.strongbox.users.service.impl.UserServiceImpl;
import org.carlspring.strongbox.xml.parsers.GenericParser;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.swagger.annotations.*;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@PreAuthorize("hasAuthority('ADMIN')")
@RequestMapping(value = "/configuration/authorization")
@Api(value = "/configuration/authorization")
public class AuthorizationConfigController
        extends BaseArtifactController
{

    @Inject
    AuthorizationConfigProvider configProvider;

    @Inject
    UserService userService;

    @Inject
    CacheManager cacheManager;

    @Inject
    AnonymousAuthenticationFilter anonymousAuthenticationFilter;

    private AuthorizationConfig config;

    private ResponseEntity processConfig(Consumer<AuthorizationConfig> consumer)
    {
        return processConfig(consumer, config -> ResponseEntity.ok()
                                                               .build());
    }

    private ResponseEntity processConfig(Consumer<AuthorizationConfig> consumer,
                                         CustomSuccessResponseBuilder customSuccessResponseBuilder)
    {
        Optional<AuthorizationConfig> configOptional = configProvider.getConfig();

        if (configOptional.isPresent())
        {
            try
            {
                config = configOptional.get();

                if (consumer != null)
                {
                    consumer.accept(config);
                }

                return customSuccessResponseBuilder.build(config);
            }
            catch (Exception e)
            {
                logger.error("Error during config processing.", e);
                return toError("Error during config processing: " + e.getLocalizedMessage());
            }
        }
        else
        {
            return toError("Unable to locate AuthorizationConfig to update...");
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Add role

    @ApiOperation(value = "Used to add new roles")
    @ApiResponses(value = { @ApiResponse(code = 200,
                                         message = "The role was created successfully."),
                            @ApiResponse(code = 400,
                                         message = "An error occurred.") })
    @RequestMapping(value = "role",
                    method = RequestMethod.POST,
                    consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity addRole(@RequestBody String serializedJson)
            throws JAXBException
    {

        GenericParser<Role> parser = new GenericParser<>(Role.class);
        Role role = parser.deserialize(serializedJson);

        logger.info("Trying to add new role from JSON\n" + serializedJson);
        logger.debug(role.toString());
        return processConfig(config ->
                             {
                                 //  Role role = read(json, Role.class);
                                 boolean result = config.getRoles()
                                                        .getRoles()
                                                        .add(role);

                                 if (result)
                                 {
                                     configProvider.updateConfig(config);
                                     logger.info("Successfully added new role " + role.getName());
                                 }
                                 else
                                 {
                                     logger.warn("Unable to add new role " + role.getName());
                                 }
                             });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // View authorization config as XML file

    @ApiOperation(value = "Retrieves the strongbox-authorization.xml configuration file.")
    @ApiResponses(value = { @ApiResponse(code = 200,
                                         message = ""),
                            @ApiResponse(code = 500,
                                         message = "An error occurred.") })
    @RequestMapping(value = "/xml",
                    method = RequestMethod.GET,
                    produces = { MediaType.APPLICATION_XML_VALUE,
                                 MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity getAuthorizationConfig()
            throws JAXBException
    {
        logger.debug("Trying to receive authorization config as XML / JSON file...");

        return processConfig(null, ResponseEntity::ok);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Revoke role by name

    @ApiOperation(value = "Deletes a role by name.",
                  position = 3)
    @ApiResponses(value = { @ApiResponse(code = 200,
                                         message = "The role was deleted."),
                            @ApiResponse(code = 400,
                                         message = "Bad request.")
    })
    @RequestMapping(value = "role/{name}",
                    method = RequestMethod.DELETE)
    public ResponseEntity deleteRole(@ApiParam(value = "The name of the role",
                                               required = true)
                                     @PathVariable("name") String name)
            throws Exception
    {
        return processConfig(config ->
                             {

                                 // find Privilege by name
                                 Role target = null;
                                 for (Role role : config.getRoles()
                                                        .getRoles())
                                 {
                                     if (role.getName()
                                             .equalsIgnoreCase(name))
                                     {
                                         target = role;
                                         break;
                                     }
                                 }
                                 if (target != null)
                                 {
                                     // revoke role from current config
                                     config.getRoles()
                                           .getRoles()
                                           .remove(target);
                                     configProvider.updateConfig(config);

                                     // revoke role from every user that exists in the system
                                     getAllUsers().forEach(user ->
                                                           {
                                                               if (user.getRoles().remove(name.toUpperCase()))
                                                               {
                                                                   // evict such kind of users from cache
                                                                   cacheManager.getCache(UserServiceImpl.USERS_CACHE)
                                                                               .evict(user);
                                                               }
                                                           });
                                 }
                             });
    }


    // ----------------------------------------------------------------------------------------------------------------
    // Assign privileges to the anonymous user

    @RequestMapping(value = "anonymous/privileges",
                    method = RequestMethod.POST,
                    consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity addPrivilegesToAnonymous(@RequestBody List<Privilege> privileges)
    {
        return processConfig(config -> privileges.forEach(this::addAnonymousAuthority));
    }


    // ----------------------------------------------------------------------------------------------------------------
    // Assign roles to the anonymous user

    @RequestMapping(value = "anonymous/roles",
                    method = RequestMethod.POST,
                    consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity addRolesToAnonymous(List<Role> roles)
    {
        return processConfig(config -> roles.forEach(role -> config.getRoles()
                                                                   .getRoles()
                                                                   .stream()
                                                                   .filter(
                                                                           role1 -> role1.getName()
                                                                                         .equalsIgnoreCase(
                                                                                                 role.getName()))
                                                                   .forEach(
                                                                           foundedRole -> foundedRole.getPrivileges()
                                                                                                     .forEach(
                                                                                                             this::addAnonymousAuthority))));
    }

    private void addAnonymousAuthority(Privilege authority)
    {
        addAnonymousAuthority(authority.getName());
    }

    private void addAnonymousAuthority(String authority)
    {
        SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(authority.toUpperCase());
        anonymousAuthenticationFilter.getAuthorities()
                                     .add(simpleGrantedAuthority);
    }

    private List<User> getAllUsers()
    {
        return userService.findAll()
                          .orElse(new LinkedList<>());
    }

    private interface CustomSuccessResponseBuilder
    {

        ResponseEntity build(AuthorizationConfig config)
                throws JAXBException;
    }

}