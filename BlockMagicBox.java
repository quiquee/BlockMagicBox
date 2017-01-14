/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//package com.backmagicbox.core;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import org.slf4j.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * ForwardingService demonstrates basic usage of the library. It sits on the network and when it receives coins, simply
 * sends them onwards to an address given on the command line.
 */
public class BlockMagicBox {
    private static Address forwardingAddress;
    private static WalletAppKit kit;
    final static Logger logger = LoggerFactory.getLogger(BlockMagicBox.class);
    private static List<byte[]> changeAddresses = new ArrayList<byte[]>();
    private static Address sendToAddress  ;        
    private static byte[] sendToAddressHash160 ;
    private static NetworkParameters params;
    public static void main(String[] args) throws Exception {
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        
        BriefLogFormatter.init();
        if (args.length < 1) {
            System.err.println("Usage: address-to-send-back-to [regtest|testnet]");
            return;
        }

        // Figure out which network we should connect to. Each one gets its own set of files.
        
        String filePrefix;
        if (args.length > 1 && args[1].equals("testnet")) {
            params = TestNet3Params.get();
            filePrefix = "blockmagicbox-testnet";
        } else if (args.length > 1 && args[1].equals("regtest")) {
            params = RegTestParams.get();
            filePrefix = "blockmagicbox-regtest";
        } else {
            params = MainNetParams.get();
            filePrefix = "blockmagicbox";
        }
        // Parse the address given as the first parameter.
        forwardingAddress = Address.fromBase58(params, args[0]);

        // Start up a basic app using a class that automates some boilerplate.
        kit = new WalletAppKit(params, new File(System.getProperty("user.home") ), filePrefix) {
                @Override
                protected void onSetupCompleted () {
                    // Don't make the user wait for confirmations for now, as the intention is they're sending it
                    // their own money!
                    kit.wallet().allowSpendingUnconfirmedTransactions();
                    //Platform.runLater(controller::onBitcoinSetup);

                }
            };        


        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();
        sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);        
        sendToAddressHash160 = sendToAddress.getHash160() ;

        
        // We want to know when we receive money.
        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
                @Override
                public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                    // Runs in the dedicated "user thread" (see bitcoinj docs for more info on this).
                    //
                    // The transaction "tx" can either be pending, or included into a block (we didn't see the broadcast).
                    Coin value = tx.getValueSentToMe(w);
                    logger.info("Received unconfirmed transaction for " + value.toFriendlyString() + ": " + tx);
		    boolean forwardThis ;
                    forwardThis = true;
		    List<TransactionOutput> outputs = tx.getOutputs();
		    byte[] tmpHash160;

		    for(TransactionOutput out : outputs) {
	               tmpHash160=out.getAddressFromP2PKHScript(params).getHash160() ;

			 if ( changeAddresses.contains(tmpHash160))
			    {
			      forwardThis = false ;

			    }
		         
                       }


		    if (forwardThis) {
                       logger.info("Forwarding the coins to " + forwardingAddress);
                       forwardCoins(tx, forwardingAddress );    
                    
                    // Wait until it's made it into the block chain (may run immediately if it's already there).
                    //
                    // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
                    // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
                    // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
                    // case of waiting for a block.
		    
                    Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                            @Override
                            public void onSuccess(TransactionConfidence result) {
                                //forwardCoins(tx);
                                logger.info("Incoming transaction was confirmed: " + tx);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                // This kind of future can't fail, just rethrow in case something weird happens.
                                throw new RuntimeException(t);
                            }
                        });
                    } else {
	               logger.debug("This is our own change, keep it here");
                    }
                }
            });


	logger.info("Send coins to: " + sendToAddress);
        logger.info("Waiting for coins to arrive. Press Ctrl-C to quit.");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}
    }

    private static void forwardCoins(Transaction tx, Address addressToForward) {
	//Logger logger = LoggerFactory.getLogger(BlockMagicBox.class);
        try {
            Coin value = tx.getValueSentToMe(kit.wallet());

            // Now send the coins back! Send with a small fee attached to ensure rapid confirmation.
            final Coin amountToSend = value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
            final Wallet.SendResult sendResult = kit.wallet().sendCoins(kit.peerGroup(), addressToForward, amountToSend);

	    saveRestAddress(sendResult.tx) ;

            checkNotNull(sendResult);  // We should never try to send more coins than we have!
            logger.info("Forwarding " + value.toFriendlyString());

            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(new Runnable() {
                    @Override
                    public void run() {
                        // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                        System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                    }
                }, MoreExecutors.sameThreadExecutor());
        }
        catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    // Save the change address of the forwarding transaction so we will not
    // Forward the change , only what was paid. 
    private static void saveRestAddress( Transaction tx ) {

	List<TransactionOutput> outputs = tx.getOutputs();
	byte[] tmpHash160 ;

	for(TransactionOutput out : outputs){
	    tmpHash160=out.getAddressFromP2PKHScript(params).getHash160() ;

	    if ( ! tmpHash160.equals(sendToAddressHash160 )) 	
		{
		   changeAddresses.add(tmpHash160) ;
		   logger.info("Registering change address: " + tmpHash160) ;
		}
	}

    }

}
