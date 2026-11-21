import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  vhayaApi,
  type CasePriority,
  type CaseStatus,
  type AccountDeletionRequest,
  type PersonRole,
  type SupportCategory,
  type SupportCase,
  type GetAdminRidesStatus,
  type DriverSubscriptionPlanInput,
  type DriverPromotionInput,
  type AdminDriverSubscriptionsPage,
  type DriverSubscriptionStatus,
} from "@/lib/vhaya-api";

export const adminQueryKeys = {
  overview: ["admin", "overview"] as const,
  activity: ["admin", "activity"] as const,
  onlineDrivers: ["admin", "online-drivers"] as const,
  ridesBase: ["admin", "rides"] as const,
  rides: (options: {
    q?: string;
    status?: GetAdminRidesStatus;
    page?: number;
    limit?: number;
  }) => ["admin", "rides", options] as const,
  peopleBase: ["admin", "people"] as const,
  people: (options: { q?: string; type?: string; page?: number }) =>
    ["admin", "people", options] as const,
  person: (userId: string) => ["admin", "person", userId] as const,
  applications: ["admin", "driver-applications"] as const,
  compliance: ["admin", "driver-compliance"] as const,
  support: ["admin", "support-cases"] as const,
  payments: ["admin", "payments"] as const,
  wallets: ["admin", "wallets"] as const,
  payouts: ["admin", "payouts"] as const,
  batches: ["admin", "payout-batches"] as const,
  payoutRecipients: ["admin", "payout-recipients"] as const,
  payoutBanks: ["admin", "payout-banks"] as const,
  payoutBatchDetails: ["admin", "payout-batch-detail"] as const,
  payoutBatch: (batchId: string) =>
    ["admin", "payout-batch-detail", batchId] as const,
  accountDeletions: ["admin", "account-deletion-requests"] as const,
  driverSubscriptionPlans: ["admin", "driver-subscription-plans"] as const,
  driverSubscriptionsBase: ["admin", "driver-subscriptions"] as const,
  driverSubscriptions: (options: { q?: string; page?: number; limit?: number }) =>
    ["admin", "driver-subscriptions", options] as const,
  driverPromotions: ["admin", "driver-promotions"] as const,
};

const defaultQueryOptions = {
  staleTime: 15_000,
  refetchOnWindowFocus: true,
  retry: 1,
};

function dashboardQueryOptions(refetchInterval: number | false) {
  return {
    staleTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
    refetchInterval,
    retry: 1,
  };
}

export function useAdminOverview(refetchInterval: number | false = false) {
  return useQuery({
    queryKey: adminQueryKeys.overview,
    queryFn: vhayaApi.overview,
    ...dashboardQueryOptions(refetchInterval),
  });
}

export function useAdminActivity(
  limit = 50,
  refetchInterval: number | false = false,
) {
  return useQuery({
    queryKey: [...adminQueryKeys.activity, limit],
    queryFn: () => vhayaApi.activity(limit),
    ...dashboardQueryOptions(refetchInterval),
  });
}

export function useOnlineDrivers(refetchInterval = 15_000) {
  return useQuery({
    queryKey: adminQueryKeys.onlineDrivers,
    queryFn: vhayaApi.onlineDrivers,
    staleTime: 5_000,
    refetchOnWindowFocus: true,
    refetchInterval,
    retry: 1,
  });
}

export function useRides(options: {
  q?: string;
  status?: GetAdminRidesStatus;
  page?: number;
  limit?: number;
}) {
  return useQuery({
    queryKey: adminQueryKeys.rides(options),
    queryFn: () => vhayaApi.rides(options),
    ...defaultQueryOptions,
  });
}

export function usePeople(options: {
  q?: string;
  type?: "all" | "requesters" | "drivers";
  page?: number;
  limit?: number;
}) {
  return useQuery({
    queryKey: adminQueryKeys.people(options),
    queryFn: () => vhayaApi.people(options),
    ...defaultQueryOptions,
  });
}

export function usePerson(userId: string) {
  return useQuery({
    queryKey: adminQueryKeys.person(userId),
    queryFn: () => vhayaApi.person(userId),
    enabled: Boolean(userId),
    ...defaultQueryOptions,
  });
}

export function useDriverApplications() {
  return useQuery({
    queryKey: adminQueryKeys.applications,
    queryFn: vhayaApi.driverApplications,
    ...defaultQueryOptions,
  });
}

export function useDriverCompliance() {
  return useQuery({
    queryKey: adminQueryKeys.compliance,
    queryFn: vhayaApi.driverCompliance,
    ...defaultQueryOptions,
  });
}

export function useSupportCases() {
  return useQuery({
    queryKey: adminQueryKeys.support,
    queryFn: vhayaApi.supportCases,
    ...defaultQueryOptions,
  });
}

export function usePayments() {
  return useQuery({
    queryKey: adminQueryKeys.payments,
    queryFn: () => vhayaApi.payments(100),
    ...defaultQueryOptions,
  });
}

export function useDriverSubscriptionPlans() {
  return useQuery({
    queryKey: adminQueryKeys.driverSubscriptionPlans,
    queryFn: vhayaApi.driverSubscriptionPlans,
    ...defaultQueryOptions,
  });
}

export function useAdminDriverSubscriptions(options: {
  q?: string;
  page?: number;
  limit?: number;
}) {
  return useQuery({
    queryKey: adminQueryKeys.driverSubscriptions(options),
    queryFn: () => vhayaApi.driverSubscriptions(options),
    ...defaultQueryOptions,
  });
}

export function useDriverPromotions() {
  return useQuery({
    queryKey: adminQueryKeys.driverPromotions,
    queryFn: vhayaApi.driverPromotions,
    ...defaultQueryOptions,
  });
}

export function useWallets() {
  return useQuery({
    queryKey: adminQueryKeys.wallets,
    queryFn: vhayaApi.wallets,
    ...defaultQueryOptions,
  });
}

export function usePayouts() {
  return useQuery({
    queryKey: adminQueryKeys.payouts,
    queryFn: vhayaApi.payouts,
    ...defaultQueryOptions,
  });
}

export function usePayoutBatches() {
  return useQuery({
    queryKey: adminQueryKeys.batches,
    queryFn: vhayaApi.payoutBatches,
    ...defaultQueryOptions,
  });
}

export function usePayoutBatch(batchId: string, enabled = true) {
  return useQuery({
    queryKey: adminQueryKeys.payoutBatch(batchId),
    queryFn: () => vhayaApi.payoutBatch(batchId),
    enabled: Boolean(batchId) && enabled,
    ...defaultQueryOptions,
  });
}

export function usePayoutRecipients() {
  return useQuery({
    queryKey: adminQueryKeys.payoutRecipients,
    queryFn: vhayaApi.payoutRecipients,
    ...defaultQueryOptions,
  });
}

export function usePayoutBanks(enabled: boolean) {
  return useQuery({
    queryKey: adminQueryKeys.payoutBanks,
    queryFn: vhayaApi.payoutBanks,
    enabled,
    ...defaultQueryOptions,
  });
}

function useRefreshAdminData() {
  const queryClient = useQueryClient();
  return async (...keys: readonly (readonly unknown[])[]) => {
    await Promise.all(
      keys.map((queryKey) =>
        queryClient.invalidateQueries({ queryKey: [...queryKey] }),
      ),
    );
  };
}

export function useUpdateSupportCase() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      caseId,
      input,
    }: {
      caseId: string;
      input: {
        status?: CaseStatus;
        priority?: CasePriority;
        category?: SupportCategory;
        assignedTo?: "me" | null;
        staffNote?: string | null;
      };
    }) => vhayaApi.updateSupportCase(caseId, input),
    onSuccess: () =>
      refresh(
        adminQueryKeys.support,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useReplyToSupportCase() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ caseId, message }: { caseId: string; message: string }) =>
      vhayaApi.replyToSupportCase(caseId, message),
    onSuccess: () =>
      refresh(
        adminQueryKeys.support,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useUpdatePersonRole() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: PersonRole }) =>
      vhayaApi.updateRole(userId, role),
    onSuccess: (_result, variables) =>
      refresh(
        adminQueryKeys.peopleBase,
        adminQueryKeys.person(variables.userId),
        adminQueryKeys.activity,
      ),
  });
}

export function useSuspendPerson() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ userId, reason }: { userId: string; reason: string }) =>
      vhayaApi.suspendPerson(userId, reason),
    onSuccess: (_result, variables) =>
      refresh(
        adminQueryKeys.peopleBase,
        adminQueryKeys.person(variables.userId),
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useUnsuspendPerson() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ userId }: { userId: string }) =>
      vhayaApi.unsuspendPerson(userId),
    onSuccess: (_result, variables) =>
      refresh(
        adminQueryKeys.peopleBase,
        adminQueryKeys.person(variables.userId),
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useCancelRide() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ rideId }: { rideId: string }) => vhayaApi.cancelRide(rideId),
    onSuccess: () =>
      refresh(
        adminQueryKeys.ridesBase,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useReviewPerson() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      userId,
      input,
    }: {
      userId: string;
      input: {
        subjectType: "requester" | "driver";
        outcome: "reviewed" | "needs_follow_up" | "approved" | "rejected";
        note?: string;
        checksConfirmed?: boolean;
      };
    }) => vhayaApi.reviewPerson(userId, input),
    onSuccess: (_result, variables) =>
      refresh(
        adminQueryKeys.person(variables.userId),
        adminQueryKeys.peopleBase,
        adminQueryKeys.applications,
        adminQueryKeys.compliance,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useUpdateCompliance() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      userId,
      input,
    }: {
      userId: string;
      input: {
        documentType:
          | "identity"
          | "driver_license"
          | "vehicle_registration"
          | "operating_permit";
        status: "not_reviewed" | "verified" | "needs_follow_up" | "not_required";
        expiresOn?: string | null;
        note?: string;
      };
    }) => vhayaApi.updateCompliance(userId, input),
    onSuccess: () =>
      refresh(adminQueryKeys.compliance, adminQueryKeys.applications),
  });
}

export function useRequestRefund() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ paymentId, reason }: { paymentId: string; reason: string }) =>
      vhayaApi.requestRefund(paymentId, reason),
    onSuccess: () =>
      refresh(
        adminQueryKeys.payments,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

function refreshSubscriptionAdminData(
  refresh: ReturnType<typeof useRefreshAdminData>,
) {
  return refresh(
    adminQueryKeys.driverSubscriptionPlans,
    adminQueryKeys.driverSubscriptionsBase,
    adminQueryKeys.driverPromotions,
    adminQueryKeys.activity,
  );
}

export function useCreateDriverSubscriptionPlan() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: (input: DriverSubscriptionPlanInput) =>
      vhayaApi.createDriverSubscriptionPlan(input),
    onSuccess: () => refreshSubscriptionAdminData(refresh),
  });
}

export function useUpdateDriverSubscriptionPlan() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      planId,
      input,
    }: {
      planId: string;
      input: Partial<DriverSubscriptionPlanInput>;
    }) => vhayaApi.updateDriverSubscriptionPlan(planId, input),
    onSuccess: () => refreshSubscriptionAdminData(refresh),
  });
}

export function useUpdateAdminDriverSubscription() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      driverId,
      input,
    }: {
      driverId: string;
      input: {
        planId?: string;
        status?: DriverSubscriptionStatus;
        expiresAt?: string | null;
        reason?: string;
      };
    }) => vhayaApi.updateDriverSubscription(driverId, input),
    onSuccess: () => refreshSubscriptionAdminData(refresh),
  });
}

export function useCreateDriverPromotion() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: (input: DriverPromotionInput) =>
      vhayaApi.createDriverPromotion(input),
    onSuccess: () => refreshSubscriptionAdminData(refresh),
  });
}

export function useUpdateDriverPromotion() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      promotionId,
      input,
    }: {
      promotionId: string;
      input: Partial<DriverPromotionInput>;
    }) => vhayaApi.updateDriverPromotion(promotionId, input),
    onSuccess: () => refreshSubscriptionAdminData(refresh),
  });
}

export function useCreatePayout() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: (input: Parameters<typeof vhayaApi.createPayout>[0]) =>
      vhayaApi.createPayout(input),
    onSuccess: () =>
      refresh(
        adminQueryKeys.payouts,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useReconcilePayout() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      payoutId,
      reconciliationStatus,
      note,
    }: {
      payoutId: string;
      reconciliationStatus: "matched" | "exception";
      note?: string;
    }) =>
      vhayaApi.reconcilePayout(payoutId, { reconciliationStatus, note }),
    onSuccess: () =>
      refresh(adminQueryKeys.payouts, adminQueryKeys.activity),
  });
}

export function useRunWeeklyPayouts() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      periodStart,
      periodEnd,
    }: {
      periodStart: string;
      periodEnd: string;
    }) => vhayaApi.runWeeklyPayouts(periodStart, periodEnd),
    onSuccess: () =>
      refresh(
        adminQueryKeys.payouts,
        adminQueryKeys.batches,
        adminQueryKeys.payments,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useApprovePayoutBatch() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ batchId }: { batchId: string }) =>
      vhayaApi.approvePayoutBatch(batchId),
    onSuccess: () =>
      refresh(adminQueryKeys.batches, adminQueryKeys.activity),
  });
}

export function useSavePayoutRecipient() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: (input: {
      driverId: string;
      name: string;
      accountNumber: string;
      bankCode: string;
    }) => vhayaApi.savePayoutRecipient(input),
    onSuccess: () =>
      refresh(
        adminQueryKeys.payoutRecipients,
        adminQueryKeys.payoutBatchDetails,
        adminQueryKeys.activity,
      ),
  });
}

export function useStartPayoutBatchTransfers() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      batchId,
      retryFailed,
      reviewToken,
    }: {
      batchId: string;
      retryFailed?: boolean;
      reviewToken: string;
    }) =>
      vhayaApi.startPayoutBatchTransfers(batchId, {
        confirmed: true,
        retryFailed,
        reviewToken,
      }),
    onSuccess: () =>
      refresh(
        adminQueryKeys.batches,
        adminQueryKeys.payouts,
        adminQueryKeys.payoutBatchDetails,
        adminQueryKeys.activity,
      ),
  });
}

export function useVerifyPayoutTransfer() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ payoutId }: { payoutId: string }) =>
      vhayaApi.verifyPayoutTransfer(payoutId),
    onSuccess: () =>
      refresh(
        adminQueryKeys.payouts,
        adminQueryKeys.batches,
        adminQueryKeys.payoutBatchDetails,
        adminQueryKeys.activity,
      ),
  });
}

export function useRecordExternalPayoutBatchPayment() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ batchId }: { batchId: string }) =>
      vhayaApi.recordExternalPayoutBatchPayment(batchId),
    onSuccess: () =>
      refresh(
        adminQueryKeys.batches,
        adminQueryKeys.payoutBatchDetails,
        adminQueryKeys.activity,
      ),
  });
}

export function useAccountDeletionRequests() {
  return useQuery({
    queryKey: adminQueryKeys.accountDeletions,
    queryFn: vhayaApi.accountDeletionRequests,
    ...defaultQueryOptions,
  });
}

export function useCompleteAccountDeletion() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({ requestId }: { requestId: string }) =>
      vhayaApi.completeAccountDeletion(requestId),
    onSuccess: () =>
      refresh(
        adminQueryKeys.accountDeletions,
        adminQueryKeys.peopleBase,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function useRejectAccountDeletion() {
  const refresh = useRefreshAdminData();
  return useMutation({
    mutationFn: ({
      requestId,
      reason,
    }: {
      requestId: string;
      reason: string;
    }) => vhayaApi.rejectAccountDeletion(requestId, reason),
    onSuccess: () =>
      refresh(
        adminQueryKeys.accountDeletions,
        adminQueryKeys.overview,
        adminQueryKeys.activity,
      ),
  });
}

export function updateSupportCaseCache(
  cases: SupportCase[] | undefined,
  updated: SupportCase,
): SupportCase[] | undefined {
  return cases?.map((item) => (item.id === updated.id ? updated : item));
}
