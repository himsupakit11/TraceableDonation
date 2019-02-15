package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@StartableByRPC
@InitiatingFlow
class StartCampaign(private val newCampaign: Campaign): FlowLogic<SignedTransaction>(){

    @Suspendable
    override fun call(): SignedTransaction{
        //Pick notary
        val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
        //Assemble the campaign components
        val startcommand = Command(CampaignContract.Commands.Start(), listOf(ourIdentity.owningKey))
        val outputState = StateAndContract(newCampaign,CampaignContract.ID)
        //Build, sign and record the campaign
        val utx = TransactionBuilder(notary = notary).withItems(outputState,startcommand)
        val stx = serviceHub.signInitialTransaction(utx)

//        val otherPartyFlow = initiateFlow(newCampaign.recipient)
//        val fullySignedTxx = subFlow(CollectSignaturesFlow(stx, setOf(otherPartyFlow)))



        val ftx = subFlow(FinalityFlow(stx))
        //broadcast campaign to all parties on the network
//       subFlow(BroadcastTransaction(ftx))
        return ftx
    }
}
//
//@InitiatedBy(StartCampaign::class)
//class StartCampaignResponder(private val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
//    @Suspendable
//    override fun call(): SignedTransaction {
//        val flow = object: SignTransactionFlow(otherFlow) {
//            @Suspendable
//            override fun checkTransaction(stx: SignedTransaction) {
//                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//            }
//        }
//        val stx = subFlow(flow)
//        return waitForLedgerCommit(stx.id)
//    }
//}

@InitiatingFlow
class BroadcastTransaction(val stx: SignedTransaction): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Get a list of all identities from the network map cache.
        val everyone = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }
        // Filter out the notary identities and remove our identity.
        val everyoneButMeAndNotary = everyone.filter { serviceHub.networkMapCache.isNotary(it) } - ourIdentity
        // Create a session for each remaining party.
        val sessions = everyoneButMeAndNotary.map { initiateFlow(it) }
        // Send the transaction to all the remaining parties.
        sessions.forEach { subFlow(SendTransactionFlow(it,stx )) }

    }

}
// The responder can only observe the states
@InitiatedBy(BroadcastTransaction::class)
//Flow session used for sending and receiving transaction between parties
class  RecordTransactionAsObserver(val otherSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val flow = ReceiveTransactionFlow(
                otherSideSession = otherSession,
                checkSufficientSignatures = true,
                statesToRecord = StatesToRecord.ALL_VISIBLE
        )
        subFlow(flow)
    }
}