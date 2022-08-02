/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.session.control;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConcurrentSessionVerifierTest {
  private ConcurrentSessionVerifier verifier;
  private Map<String, String> options;
  private DefaultTokenAuthorityService tokenAuthority;
  private JWTokenAttributes jwtAttributesForAdmin;
  private JWTokenAttributes jwtAttributesForTom;
  private JWT adminToken1;
  private JWT adminToken2;
  private JWT adminToken3;
  private JWT adminToken4;
  private JWT adminToken5;
  private JWT adminToken6;
  private JWT tomToken1;
  private JWT tomToken2;
  private JWT tomToken3;
  private JWT tomToken4;
  private JWT tomToken5;
  private JWT tomToken6;

  @Before
  public void setUp() throws AliasServiceException, IOException, ServiceLifecycleException {
    verifier = new ConcurrentSessionVerifier();
    options = new HashMap<>();

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());

    tokenAuthority = new DefaultTokenAuthorityService();
    tokenAuthority.setAliasService(as);
    tokenAuthority.setKeystoreService(ks);
    tokenAuthority.init(config, new HashMap<>());
    tokenAuthority.start();

    jwtAttributesForAdmin = new JWTokenAttributesBuilder()
            .setIssuer("KNOXSSO")
            .setUserName("admin")
            .setAudiences(new ArrayList<>())
            .setAlgorithm("RS256")
            .setExpires(-1)
            .setSigningKeystoreName(null)
            .setSigningKeystoreAlias(null)
            .setSigningKeystorePassphrase(null)
            .build();
    jwtAttributesForTom = new JWTokenAttributesBuilder()
            .setIssuer("KNOXSSO")
            .setUserName("tom")
            .setAudiences(new ArrayList<>())
            .setAlgorithm("RS256")
            .setExpires(-1)
            .setSigningKeystoreName(null)
            .setSigningKeystoreAlias(null)
            .setSigningKeystorePassphrase(null)
            .build();
    try {
      adminToken1 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken2 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken3 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken4 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken5 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken6 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      tomToken1 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken2 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken3 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken4 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken5 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken6 = tokenAuthority.issueToken(jwtAttributesForTom);
    } catch (TokenServiceException ignored) {
    }
  }

  private GatewayConfig mockConfig(Set<String> privilegedUsers, Set<String> nonPrivilegedUsers, int privilegedUsersLimit, int nonPrivilegedUsersLimit) {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getPrivilegedUsers()).andReturn(privilegedUsers);
    EasyMock.expect(config.getNonPrivilegedUsers()).andReturn(nonPrivilegedUsers);
    EasyMock.expect(config.getPrivilegedUsersConcurrentSessionLimit()).andReturn(privilegedUsersLimit);
    EasyMock.expect(config.getNonPrivilegedUsersConcurrentSessionLimit()).andReturn(nonPrivilegedUsersLimit);
    EasyMock.replay(config);
    return config;
  }


  @Test
  public void userIsInNeitherOfTheGroups() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("guest")), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken1.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken2.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken3.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken4.toString()));
  }

  @Test
  public void userIsInBothOfTheGroups() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin", "tom")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken1.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken2.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken3.toString()));

    config = mockConfig(new HashSet<>(Arrays.asList("admin", "tom")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 4);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken1.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken2.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken3.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken4.toString()));
  }

  @Test
  public void userIsPrivileged() throws ServiceLifecycleException, TokenServiceException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken1.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken2.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken3.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("admin", adminToken4.toString()));
    verifier.sessionEndedForUser("admin", adminToken1.toString());
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken5.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("admin", adminToken6.toString()));
  }

  @Test
  public void userIsNotPrivileged() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken1.toString()));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken2.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken3.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken4.toString()));
    verifier.sessionEndedForUser("tom", tomToken1.toString());
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken5.toString()));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken6.toString()));
  }

  @Test
  public void privilegedLimitIsZero() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 0, 2);
    verifier.init(config, options);

    Assert.assertFalse(verifier.verifySessionForUser("admin", adminToken1.toString()));
  }

  @Test
  public void nonPrivilegedLimitIsZero() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 0);
    verifier.init(config, options);

    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken1.toString()));
  }

  @Test
  public void sessionsDoNotGoToNegative() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 2, 2);
    verifier.init(config, options);

    Assert.assertNull(verifier.getUserConcurrentSessionCount("admin"));
    verifier.verifySessionForUser("admin", adminToken1.toString());
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("admin").intValue());
    verifier.sessionEndedForUser("admin", adminToken1.toString());
    Assert.assertNull(verifier.getUserConcurrentSessionCount("admin"));
    verifier.sessionEndedForUser("admin", adminToken1.toString());
    Assert.assertNull(verifier.getUserConcurrentSessionCount("admin"));
    verifier.verifySessionForUser("admin", adminToken2.toString());
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("admin").intValue());

    Assert.assertNull(verifier.getUserConcurrentSessionCount("tom"));
    verifier.verifySessionForUser("tom", tomToken1.toString());
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("tom").intValue());
    verifier.sessionEndedForUser("tom", tomToken1.toString());
    Assert.assertNull(verifier.getUserConcurrentSessionCount("tom"));
    verifier.sessionEndedForUser("tom", tomToken1.toString());
    Assert.assertNull(verifier.getUserConcurrentSessionCount("tom"));
    verifier.verifySessionForUser("tom", tomToken2.toString());
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("tom").intValue());
  }

  @Test
  public void negativeLimitMeansUnlimited() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), -2, -2);
    verifier.init(config, options);

    for (int i = 0; i < 10; i++) {
      try {
        JWT token = tokenAuthority.issueToken(jwtAttributesForAdmin);
        Assert.assertTrue(verifier.verifySessionForUser("admin", token.toString()));
        token = tokenAuthority.issueToken(jwtAttributesForTom);
        Assert.assertTrue(verifier.verifySessionForUser("tom", token.toString()));
      } catch (TokenServiceException ignored) {
      }
    }
  }

  @Test
  public void expiredTokensAreNotCounted() throws ServiceLifecycleException, TokenServiceException, InterruptedException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 3);
    verifier.init(config, options);

    JWTokenAttributes jwtAttributesForTomFor10Sec = new JWTokenAttributesBuilder()
            .setIssuer("KNOXSSO")
            .setUserName("tom")
            .setAudiences(new ArrayList<>())
            .setAlgorithm("RS256")
            .setExpires(System.currentTimeMillis() + 1000)
            .setSigningKeystoreName(null)
            .setSigningKeystoreAlias(null)
            .setSigningKeystorePassphrase(null)
            .build();

    JWT tomToken = tokenAuthority.issueToken(jwtAttributesForTom);
    verifier.verifySessionForUser("tom", tomToken.toString());
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("tom").intValue());
    tomToken = tokenAuthority.issueToken(jwtAttributesForTomFor10Sec);
    verifier.verifySessionForUser("tom", tomToken.toString());
    Assert.assertEquals(2, verifier.getUserConcurrentSessionCount("tom").intValue());
    tomToken = tokenAuthority.issueToken(jwtAttributesForTomFor10Sec);
    verifier.verifySessionForUser("tom", tomToken.toString());
    Assert.assertEquals(3, verifier.getUserConcurrentSessionCount("tom").intValue());
    Thread.sleep(1000L);
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("tom").intValue());

    JWTokenAttributes jwtAttributesForAdminFor10Sec = new JWTokenAttributesBuilder()
            .setIssuer("KNOXSSO")
            .setUserName("admin")
            .setAudiences(new ArrayList<>())
            .setAlgorithm("RS256")
            .setExpires(System.currentTimeMillis() + 1000)
            .setSigningKeystoreName(null)
            .setSigningKeystoreAlias(null)
            .setSigningKeystorePassphrase(null)
            .build();

    JWT adminToken = tokenAuthority.issueToken(jwtAttributesForAdmin);
    verifier.verifySessionForUser("admin", adminToken.toString());
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("admin").intValue());
    adminToken = tokenAuthority.issueToken(jwtAttributesForAdminFor10Sec);
    verifier.verifySessionForUser("admin", adminToken.toString());
    Assert.assertEquals(2, verifier.getUserConcurrentSessionCount("admin").intValue());
    adminToken = tokenAuthority.issueToken(jwtAttributesForAdminFor10Sec);
    verifier.verifySessionForUser("admin", adminToken.toString());
    Assert.assertEquals(3, verifier.getUserConcurrentSessionCount("admin").intValue());
    Thread.sleep(1000L);
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("admin").intValue());
  }
}

