import { Router, type IRouter } from "express";
import ridesRouter from "./rides";
import routesPreviewRouter from "./routes-preview";
import placesSearchRouter from "./places-search";
import paymentsRouter from "./payments";
import adminRouter from "./admin";
import walletsRouter from "./wallets";
import pushTokensRouter from "./push-tokens";
import conversationsRouter from "./conversations";
import messagesRouter from "./messages";
import authSessionsRouter from "./auth-sessions";
import accountDeletionRouter from "./account-deletion";
import driverSubscriptionsRouter from "./driver-subscriptions";
import adminDriverSubscriptionsRouter from "./admin-driver-subscriptions";

const router: IRouter = Router();

router.use(authSessionsRouter);
router.use(accountDeletionRouter);
router.use(ridesRouter);
router.use(driverSubscriptionsRouter);
router.use(placesSearchRouter);
router.use(routesPreviewRouter);
router.use(paymentsRouter);
router.use(walletsRouter);
router.use(adminDriverSubscriptionsRouter);
router.use(adminRouter);
router.use("/push-tokens", pushTokensRouter);
router.use("/conversations", conversationsRouter);
router.use("/messages", messagesRouter);

export default router;
