package com.albertoventurini.rosiesbooks.identity.web;

import com.albertoventurini.rosiesbooks.identity.api.CurrentUser;
import com.albertoventurini.rosiesbooks.identity.api.CurrentUserProvider;
import com.albertoventurini.rosiesbooks.identity.internal.DevelopmentUser;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.NewCookie.SameSite;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import org.jboss.resteasy.reactive.RestForm;

@Path("/dev/users")
@IfBuildProfile(anyOf = {"dev", "test"})
class DevelopmentUserResource {

  private final CurrentUserProvider currentUsers;

  DevelopmentUserResource(CurrentUserProvider currentUsers) {
    this.currentUsers = currentUsers;
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance index() {
    return selectorPage(null);
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response select(@RestForm String alias) {
    Optional<DevelopmentUser> selected = DevelopmentUser.fromAlias(alias);
    if (selected.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(selectorPage("Choose one of the listed development users."))
          .build();
    }

    NewCookie cookie =
        new NewCookie.Builder(DevelopmentCurrentUserProvider.COOKIE_NAME)
            .value(selected.orElseThrow().alias())
            .path("/")
            .httpOnly(true)
            .sameSite(SameSite.LAX)
            .build();
    return Response.seeOther(URI.create("/")).cookie(cookie).build();
  }

  private TemplateInstance selectorPage(String error) {
    Optional<CurrentUser> selected = currentUsers.currentUser();
    return IdentityTemplates.devUsers(
        new DevelopmentUsersPage(
            DevelopmentUser.all().stream()
                .map(
                    user ->
                        new DevelopmentUserOption(
                            user.alias(),
                            user.displayLabel(),
                            selected.equals(Optional.of(user.currentUser()))))
                .toList(),
            error));
  }
}
