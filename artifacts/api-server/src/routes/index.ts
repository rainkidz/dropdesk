import { Router, type IRouter } from "express";
import healthRouter from "./health";
import downloadsRouter from "./downloads";
import premiumRouter from "./premium";

const router: IRouter = Router();

router.use(healthRouter);
router.use(downloadsRouter);
router.use(premiumRouter);

export default router;
