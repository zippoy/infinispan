package org.infinispan.cli.user;

import static org.infinispan.cli.logging.Messages.MSG;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.wildfly.common.iteration.ByteIterator;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.WildFlyElytronPasswordProvider;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.interfaces.ScramDigestPassword;
import org.wildfly.security.password.spec.BasicPasswordSpecEncoding;
import org.wildfly.security.password.spec.DigestPasswordAlgorithmSpec;
import org.wildfly.security.password.spec.EncryptablePasswordSpec;
import org.wildfly.security.password.spec.IteratedSaltedPasswordAlgorithmSpec;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/
public class UserTool {
   public static final String DEFAULT_USERS_PROPERTIES_FILE = "users.properties";
   public static final String DEFAULT_GROUPS_PROPERTIES_FILE = "groups.properties";
   public static final String DEFAULT_REALM_NAME = "default";
   public static final String DEFAULT_SERVER_ROOT = "server";

   private static final List<String> DEFAULT_ALGORITHMS = Arrays.asList(
         ScramDigestPassword.ALGORITHM_SCRAM_SHA_1,
         ScramDigestPassword.ALGORITHM_SCRAM_SHA_256,
         ScramDigestPassword.ALGORITHM_SCRAM_SHA_384,
         ScramDigestPassword.ALGORITHM_SCRAM_SHA_512,
         DigestPassword.ALGORITHM_DIGEST_MD5,
         DigestPassword.ALGORITHM_DIGEST_SHA,
         DigestPassword.ALGORITHM_DIGEST_SHA_256,
         DigestPassword.ALGORITHM_DIGEST_SHA_384,
         DigestPassword.ALGORITHM_DIGEST_SHA_512
   );

   private final Path serverRoot;
   private final Path usersFile;
   private final Path groupsFile;
   private Properties users = new Properties();
   private Properties groups = new Properties();
   private String realm = DEFAULT_REALM_NAME;
   private Boolean plainText = false;

   public UserTool(String serverRoot) {
      this(serverRoot, DEFAULT_USERS_PROPERTIES_FILE, DEFAULT_GROUPS_PROPERTIES_FILE);
   }

   public UserTool(String serverRoot, String usersFile, String groupsFile) {
      this(serverRoot != null ? Paths.get(serverRoot) : null,
            usersFile != null ? Paths.get(usersFile) : null,
            groupsFile != null ? Paths.get(groupsFile) : null);
   }

   public UserTool(Path serverRoot, Path usersFile, Path groupsFile) {
      installSecurityProvider();
      this.serverRoot = serverRoot == null ? Paths.get("server") : serverRoot;
      if (usersFile == null) {
         this.usersFile = this.serverRoot.resolve("conf").resolve(DEFAULT_USERS_PROPERTIES_FILE);
      } else if (usersFile.isAbsolute()) {
         this.usersFile = usersFile;
      } else {
         this.usersFile = this.serverRoot.resolve("conf").resolve(usersFile);
      }
      if (groupsFile == null) {
         this.groupsFile = this.serverRoot.resolve("conf").resolve(DEFAULT_GROUPS_PROPERTIES_FILE);
      } else if (groupsFile.isAbsolute()) {
         this.groupsFile = groupsFile;
      } else {
         this.groupsFile = this.serverRoot.resolve("conf").resolve(groupsFile);
      }
      load();
   }

   private void installSecurityProvider() {
      WildFlyElytronPasswordProvider instance = WildFlyElytronPasswordProvider.getInstance();
      if (java.security.Security.getProvider(instance.getName()) == null) {
         java.security.Security.insertProviderAt(instance, 1);
      }
   }

   private void load() {
      if (Files.exists(usersFile)) {
         try (Reader reader = Files.newBufferedReader(usersFile)) {
            users.load(reader);
            // TODO: detect REALM_NAME and ALGORITHM
         } catch (IOException e) {
            throw MSG.userToolIOError(usersFile, e);
         }
      }
      if (Files.exists(groupsFile)) {
         try (Reader reader = Files.newBufferedReader(groupsFile)) {
            groups.load(reader);
         } catch (IOException e) {
            throw MSG.userToolIOError(groupsFile, e);
         }
      }
   }

   private void store() {
      store(this.realm, this.plainText);
   }

   private void store(String realm, Boolean plainText) {
      if (realm == null) {
         realm = this.realm;
      }
      if (plainText == null) {
         plainText = this.plainText;
      }
      try (Writer writer = Files.newBufferedWriter(usersFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
         users.store(writer, "$REALM_NAME=" + realm + "$\n$ALGORITHM=" + (plainText ? "clear" : "encrypted") + "$");
      } catch (IOException e) {
         throw MSG.userToolIOError(usersFile, e);
      }
      try (Writer writer = Files.newBufferedWriter(groupsFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
         groups.store(writer, null);
      } catch (IOException e) {
         throw MSG.userToolIOError(groupsFile, e);
      }
   }

   public void createUser(String username, String password, String realm, Boolean plainText, List<String> userGroups, List<String> algorithms) {
      if (users.containsKey(username)) {
         throw MSG.userToolUserExists(username);
      }
      users.put(username, plainText ? password : encryptPassword(username, realm, password, algorithms));
      groups.put(username, userGroups != null ? String.join(",", userGroups) : "");
      store(realm, plainText);
   }

   public String describeUser(String username) {
      if (users.containsKey(username)) {
         String[] userGroups = groups.containsKey(username) ? groups.getProperty(username).trim().split("\\s*,\\s*") : new String[]{};
         return MSG.userDescribe(username, realm, userGroups);
      } else {
         throw MSG.userToolNoSuchUser(username);
      }
   }

   public void removeUser(String username) {
      users.remove(username);
      groups.remove(username);
      store();
   }

   public void modifyUser(String username, String password, String realm, Boolean plainText, List<String> userGroups, List<String> algorithms) {
      if (!users.containsKey(username)) {
         throw MSG.userToolNoSuchUser(username);
      } else {
         if (password != null) { // change password
            users.put(username, plainText ? password : encryptPassword(username, realm, password, algorithms));
         }
         if (userGroups != null) { // change groups
            groups.put(username, String.join(",", userGroups));
         }
         store(realm, plainText);
      }
   }

   private String encryptPassword(String username, String realm, String password, List<String> algorithms) {
      try {
         if (algorithms == null) {
            algorithms = DEFAULT_ALGORITHMS;
         }
         StringBuilder sb = new StringBuilder();
         for (String algorithm : algorithms) {
            PasswordFactory passwordFactory = PasswordFactory.getInstance(algorithm);
            AlgorithmParameterSpec spec;
            sb.append(algorithm);
            sb.append(":");
            switch (algorithm) {
               case ScramDigestPassword.ALGORITHM_SCRAM_SHA_1:
               case ScramDigestPassword.ALGORITHM_SCRAM_SHA_256:
               case ScramDigestPassword.ALGORITHM_SCRAM_SHA_384:
               case ScramDigestPassword.ALGORITHM_SCRAM_SHA_512:
                  spec = new IteratedSaltedPasswordAlgorithmSpec(ScramDigestPassword.DEFAULT_ITERATION_COUNT, salt(ScramDigestPassword.DEFAULT_SALT_SIZE));
                  break;
               case DigestPassword.ALGORITHM_DIGEST_MD5:
               case DigestPassword.ALGORITHM_DIGEST_SHA:
               case DigestPassword.ALGORITHM_DIGEST_SHA_256:
               case DigestPassword.ALGORITHM_DIGEST_SHA_384:
               case DigestPassword.ALGORITHM_DIGEST_SHA_512:
                  spec = new DigestPasswordAlgorithmSpec(username, realm);
                  break;
               default:
                  throw MSG.userToolUnknownAlgorithm(algorithm);
            }
            Password encrypted = passwordFactory.generatePassword(new EncryptablePasswordSpec(password.toCharArray(), spec));
            byte[] encoded = BasicPasswordSpecEncoding.encode(encrypted);
            sb.append(ByteIterator.ofBytes(encoded).base64Encode().drainToString());
            sb.append(";");
         }
         return sb.toString();
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
         throw new RuntimeException(e);
      }
   }

   private static byte[] salt(int size) {
      byte[] salt = new byte[size];
      ThreadLocalRandom.current().nextBytes(salt);
      return salt;
   }

   public List<String> listUsers() {
      List<String> userList = new ArrayList<>(users.stringPropertyNames());
      Collections.sort(userList);
      return userList;
   }

   public List<String> listGroups() {
      return groups.values().stream()
            .map(o -> (String) o)
            .map(s -> s.split("\\s*,\\s*"))
            .flatMap(a -> Arrays.stream(a))
            .filter(g -> !g.isEmpty())
            .sorted()
            .distinct()
            .collect(Collectors.toList());
   }
}
