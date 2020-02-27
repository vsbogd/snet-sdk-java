package io.singularitynet.sdk.integration;

import org.junit.*;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import io.singularitynet.sdk.ethereum.WithAddress;
import io.singularitynet.sdk.ethereum.PrivateKeyIdentity;
import io.singularitynet.sdk.ethereum.MnemonicIdentity;
import io.singularitynet.sdk.registry.PriceModel;
import io.singularitynet.sdk.registry.Pricing;
import io.singularitynet.sdk.registry.PaymentGroup;
import io.singularitynet.sdk.registry.EndpointGroup;
import io.singularitynet.sdk.registry.MetadataProvider;
import io.singularitynet.sdk.mpe.PaymentChannel;
import io.singularitynet.sdk.client.Configuration;
import io.singularitynet.sdk.client.StaticConfiguration;
import io.singularitynet.sdk.client.Sdk;
import io.singularitynet.sdk.client.OnDemandPaymentChannelPaymentStrategy;
import io.singularitynet.sdk.client.ServiceClient;

public class OnDemandPaymentChannelPaymentStrategyTestIT {

    private StaticConfiguration.Builder configBuilder;
    private PrivateKeyIdentity deployer;
    private Sdk sdk;

    private EndpointGroup endpointGroup;
    private PaymentGroup paymentGroup;
    private Pricing servicePrice;
    private BigInteger cogsPerCall;
    private BigInteger expirationThreshold;

    @Before
    public void setUp() {
        this.configBuilder = IntEnv.TEST_CONFIGURATION_BUILDER;
        StaticConfiguration config = configBuilder
            .setIdentityType(Configuration.IdentityType.PRIVATE_KEY)
            .setIdentityPrivateKey(IntEnv.DEPLOYER_PRIVATE_KEY)
            .build();
        this.sdk = new Sdk(config);
        this.deployer = new PrivateKeyIdentity(IntEnv.DEPLOYER_PRIVATE_KEY);
        
        MetadataProvider metadataProvider = sdk.getMetadataProvider(
                IntEnv.TEST_ORG_ID, IntEnv.TEST_SERVICE_ID);
        this.endpointGroup = metadataProvider
            .getServiceMetadata()
            .getEndpointGroupByName(IntEnv.TEST_ENDPOINT_GROUP).get();
        this.paymentGroup = metadataProvider
            .getOrganizationMetadata()
            .getPaymentGroupById(endpointGroup.getPaymentGroupId()).get();
        this.servicePrice = endpointGroup 
            .getPricing().stream()
            .filter(pr -> pr.getPriceModel() == PriceModel.FIXED_PRICE)
            .findFirst().get();
        this.cogsPerCall = servicePrice.getPriceInCogs();
        this.expirationThreshold = paymentGroup.getPaymentDetails()
            .getPaymentExpirationThreshold();
    }

    @After
    public void tearDown() {
        sdk.shutdown();
    }
    
    @Test
    public void newChannelIsCreatedOnFirstCall() throws Exception {
        run((sdk, serviceClient) -> {
            WithAddress caller = sdk.getIdentity();

            IntEnv.makeServiceCall(serviceClient);

            Stream<PaymentChannel> channels = getChannels(caller, serviceClient);
            assertEquals("Number of payment channels", 1, channels.count());
        });
    }

    @Test
    public void oldChannelIsReusedOnSecondCall() throws Exception {
        run((sdk, serviceClient) -> {
            WithAddress caller = sdk.getIdentity();
            sdk.getBlockchainPaymentChannelManager().
                openPaymentChannel(paymentGroup, caller, cogsPerCall,
                    expirationThreshold.add(BigInteger.valueOf(1)));

            IntEnv.makeServiceCall(serviceClient);

            Stream<PaymentChannel> channels = getChannels(caller, serviceClient);
            assertEquals("Number of payment channels", 1, channels.count());
        });
    }

    @Test
    public void oldChannelAddFundsOnCall() throws Exception {
        run((sdk, serviceClient) -> {
            WithAddress caller = sdk.getIdentity();
            sdk.getBlockchainPaymentChannelManager().
                openPaymentChannel(paymentGroup, caller, BigInteger.ZERO,
                    expirationThreshold.add(BigInteger.valueOf(2)));

            IntEnv.makeServiceCall(serviceClient);

            List<PaymentChannel> channels = getChannels(caller, serviceClient)
                .collect(Collectors.toList());
            assertEquals("Number of payment channels", 1, channels.size());
            assertEquals("Payment channel balance",
                    cogsPerCall, channels.get(0).getValue());
        });
    }

    @Test
    public void oldChannelIsExtendedOnCall() throws Exception {
        run((sdk, serviceClient) -> {
            WithAddress caller = sdk.getIdentity();
            sdk.getBlockchainPaymentChannelManager().
                openPaymentChannel(paymentGroup, caller, cogsPerCall, BigInteger.ZERO);
            BigInteger blockBeforeCall = sdk.getEthereum().getEthBlockNumber();

            IntEnv.makeServiceCall(serviceClient);

            List<PaymentChannel> channels = getChannels(caller, serviceClient)
                .collect(Collectors.toList());
            assertEquals("Number of payment channels", 1, channels.size());
            assertEquals("Payment channel expiration block",
                    blockBeforeCall.add(expirationThreshold.add(BigInteger.valueOf(2))),
                    channels.get(0).getExpiration());
        });
    }

    @Test
    public void oldChannelIsExtendedAndFundsAddedOnCall() throws Exception {
        run((sdk, serviceClient) -> {
            WithAddress caller = sdk.getIdentity();
            sdk.getBlockchainPaymentChannelManager().
                openPaymentChannel(paymentGroup, caller, BigInteger.ZERO, BigInteger.ZERO);
            BigInteger blockBeforeCall = sdk.getEthereum().getEthBlockNumber();

            IntEnv.makeServiceCall(serviceClient);

            List<PaymentChannel> channels = getChannels(caller, serviceClient)
                .collect(Collectors.toList());
            assertEquals("Number of payment channels", 1, channels.size());
            assertEquals("Payment channel expiration block",
                    blockBeforeCall.add(expirationThreshold.add(BigInteger.valueOf(2))),
                    channels.get(0).getExpiration());
            assertEquals("Payment channel balance",
                    cogsPerCall, channels.get(0).getValue());
        });
    }

    private Stream<PaymentChannel> getChannels(WithAddress caller, ServiceClient serviceClient) {
        return sdk.getBlockchainPaymentChannelManager()
            .getChannelsAccessibleBy(paymentGroup.getPaymentGroupId(), caller);
    }

    private void run(BiConsumer<Sdk, ServiceClient> test) throws Exception {
        PrivateKeyIdentity caller = setupNewIdentity();

        StaticConfiguration config = configBuilder
            .setIdentityType(Configuration.IdentityType.PRIVATE_KEY)
            .setIdentityPrivateKey(caller.getCredentials().getEcKeyPair().getPrivateKey().toByteArray())
            .build();
        
        Sdk sdk = new Sdk(config);
        try {

            OnDemandPaymentChannelPaymentStrategy paymentStrategy = new OnDemandPaymentChannelPaymentStrategy(sdk);
            ServiceClient serviceClient = sdk.newServiceClient(IntEnv.TEST_ORG_ID,
                    IntEnv.TEST_SERVICE_ID, endpointGroup.getGroupName(), paymentStrategy); 
            try {
                
                test.accept(sdk, serviceClient);

            } finally {
                serviceClient.shutdownNow();
            }

        } finally {
            sdk.shutdown();
        }
    }

    private PrivateKeyIdentity setupNewIdentity() throws Exception {
        PrivateKeyIdentity identity = new MnemonicIdentity("random mnemonic #" + Math.random(), 0);

        Web3j web3j = Web3j.build(new HttpService(configBuilder.getEthereumJsonRpcEndpoint().toString()));
        try {
        Transfer.sendFunds(web3j, deployer.getCredentials(), identity.getAddress().toString(),
                BigDecimal.valueOf(1.0), Convert.Unit.ETHER).send();
        } finally {
            web3j.shutdown();
        }
        sdk.transfer(identity.getAddress(), BigInteger.valueOf(1000000));

        return identity;
    }

}
