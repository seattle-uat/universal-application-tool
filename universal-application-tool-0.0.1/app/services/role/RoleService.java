package services.role;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import javax.inject.Inject;
import models.Account;
import repository.UserRepository;
import services.CiviFormError;
import services.program.ProgramDefinition;
import services.program.ProgramNotFoundException;
import services.program.ProgramService;

/** A service for reading and updating data related to system roles. */
public class RoleService {

  private final ProgramService programService;
  private final UserRepository userRepository;

  @Inject
  public RoleService(ProgramService programRepository, UserRepository userRepository) {
    this.programService = programRepository;
    this.userRepository = userRepository;
  }

  /**
   * Get a set of {@link Account}s that have the role {@link auth.Roles#ROLE_UAT_ADMIN}.
   *
   * @return an {@link ImmutableSet} of {@link Account}s that are UAT admins.
   */
  public ImmutableSet<Account> getUatAdmins() {
    // TODO(cdanzi): implement this method
    return ImmutableSet.of();
  }

  /**
   * Promotes the set of accounts (identified by email) to the role of {@link
   * auth.Roles#ROLE_PROGRAM_ADMIN} for the given program. If an account is currently a {@link
   * auth.Roles#ROLE_UAT_ADMIN}, they will not be promoted, since UAT admins cannot be program
   * admins. Instead, we return a {@link CiviFormError} listing the admin accounts that could not be
   * promoted to program admins.
   *
   * @param programId the ID of the {@link models.Program} these accounts administer
   * @param accountEmails a {@link ImmutableSet} of account emails to make program admins
   * @return {@link Optional#empty()} if all accounts were promoted to program admins, or an {@link
   *     Optional} of a {@link CiviFormError} listing the accounts that could not be promoted to
   *     program admin
   */
  public Optional<CiviFormError> makeProgramAdmins(
      long programId, ImmutableSet<String> accountEmails) throws ProgramNotFoundException {
    ProgramDefinition program = programService.getProgramDefinition(programId);
    // Filter out UAT admins from the list of emails - a UAT admin cannot be a program admin.
    ImmutableSet<String> sysAdminEmails =
        getUatAdmins().stream().map(Account::getEmailAddress).collect(toImmutableSet());
    ImmutableSet.Builder<String> invalidEmailBuilder = ImmutableSet.builder();
    accountEmails.forEach(
        email -> {
          if (sysAdminEmails.contains(email)) {
            invalidEmailBuilder.add(email);
          } else {
            userRepository.addAdministeredProgram(email, program);
          }
        });

    ImmutableSet<String> invalidEmails = invalidEmailBuilder.build();
    if (invalidEmails.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(
          CiviFormError.of(
              String.format(
                  "The following are already CiviForm admins and could not be added as"
                      + " program admins: %s",
                  Joiner.on(", ").join(invalidEmails))));
    }
  }
}
