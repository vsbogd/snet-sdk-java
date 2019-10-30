package io.singularitynet.sdk.daemon;

import org.junit.*;
import static org.junit.Assert.*;

import java.math.BigInteger;
import com.google.protobuf.ByteString;

import io.singularitynet.sdk.test.Environment;
import io.singularitynet.sdk.ethereum.Signer;
import io.singularitynet.sdk.ethereum.PrivateKeyIdentity;
import io.singularitynet.sdk.mpe.PaymentChannel;
import io.singularitynet.daemon.escrow.StateService.ChannelStateRequest;
import io.singularitynet.sdk.common.Utils;

public class PaymentChannelStateServiceTest {

    private Environment env;

    @Before
    public void setUp() {
        env = Environment.env();
    }

    @Test
    public void signChannelStateRequest() {
        String privateKey = "89765001819765816734960087977248703971879862101523844953632906408104497565820";
        // FIXME: mock ethereum to return correct block number
        long ethereumBlock = 53;
        String mpeAddress = "0xf25186B5081Ff5cE73482AD761DB0eB0d25abfBF";
        long channelId = 42;

        Signer signer = new PrivateKeyIdentity(new BigInteger(privateKey));
        PaymentChannelStateService.MessageSigningHelper helper =
            new PaymentChannelStateService.MessageSigningHelper(env.ethereum(), signer);
        PaymentChannel channel = PaymentChannel.newBuilder()
            .setMpeContractAddress(mpeAddress)
            .build();
        ChannelStateRequest.Builder request = ChannelStateRequest.newBuilder()
            .setChannelId(ByteString.copyFrom(Utils.bigIntToBytes32(BigInteger.valueOf(channelId))));

        helper.signChannelStateRequest(channel, request);

        assertEquals("Signature", "kegbvf4a+kzqDiIkDDsWIZu2EFqbR5dQzKrSmy3w6uxhg+NuOFc09wwXSwUiO46R5FN+XQ/Yjtwgxyck4K9OhRs=",
                Utils.bytesToBase64(request.getSignature().toByteArray()));
    }

}
