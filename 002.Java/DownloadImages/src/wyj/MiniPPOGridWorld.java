package wyj;

import java.util.*;

/**
 * Minimal, runnable PPO (discrete) on a tiny 1D GridWorld with real state
 * transitions.
 * - States: positions 0..6 (0/6 terminal). Start at 3.
 * - Actions: 0=Left, 1=Right.
 * - Reward: +1 at state 6 and episode terminates; small step penalty -0.01
 * otherwise.
 * - Policy: softmax over a linear function W[a][S] with one-hot state; Value:
 * V[S].
 * - GAE advantages; PPO clipped objective with value loss and tiny entropy
 * bonus.
 *
 * Compile & Run:
 * javac MiniPPOGridWorld.java
 * java MiniPPOGridWorld
 *
 * Educational, minimal, no external deps.
 */
public class MiniPPOGridWorld {

    // =============== Environment: 1D chain =================
    static class GridWorld {
        final int N = 7; // positions 0..6; 0 and 6 are terminal
        final int START = 3; // start in the middle
        final double STEP_PENALTY = -0.01;

        int s; // current state

        int reset(Random rnd) {
            s = START;
            return s;
        }

        // step: action 0=left, 1=right
        Step step(int a) {
            if (isTerminal(s)) {
                return new Step(s, 0.0, true);
            }

            int ns = s + (a == 1 ? +1 : -1);
            ns = Math.max(0, Math.min(N - 1, ns));
            double r = STEP_PENALTY;
            boolean done = false;

            if (ns == 6) { // goal
                r = 1.0;
                done = true;
            } else if (ns == 0) { // pit/terminal (no bonus)
                r = 0.0;
                done = true;
            }

            s = ns;
            return new Step(ns, r, done);
        }

        boolean isTerminal(int st) {
            return st == 0 || st == 6;
        }
    }

    static class Step {
        int s;
        double r;
        boolean done;

        Step(int s, double r, boolean done) {
            this.s = s;
            this.r = r;
            this.done = done;
        }
    }

    // =============== Trajectory buffer ===============
    static class Transition {
        int s, a, ns;
        double r;
        double oldLogPi;
        double adv, ret; // to fill later

        Transition(int s, int a, double r, int ns, double oldLogPi) {
            this.s = s;
            this.a = a;
            this.r = r;
            this.ns = ns;
            this.oldLogPi = oldLogPi;
        }
    }

    // =============== PPO Agent (tabular linear policy/value) ===============
    static class PPOAgent {
        final int S; // number of states
        final int A = 2; // actions: 0 left, 1 right

        // Policy parameters: W[a][s] -> logits for one-hot state s
        double[][] W;
        // Value parameters: V[s]
        double[] V;

        // Hyperparams
        double gamma = 0.99;
        double lambda = 0.95;
        double clip = 0.2;
        double lrPi = 3e-3;
        double lrV = 1e-2;
        double entropyCoef = 1e-3; // tiny exploration bonus
        double valueCoef = 0.5;
        double maxGradNorm = 1.0;

        Random rnd = new Random(123);

        PPOAgent(int numStates) {
            this.S = numStates;
            W = new double[A][S];
            V = new double[S];
            // small random init for policy; value zero
            for (int a = 0; a < A; a++)
                for (int s = 0; s < S; s++)
                    W[a][s] = rnd.nextGaussian() * 0.01;
        }

        // softmax probabilities over actions given one-hot state s
        double[] probs(int s) {
            // logits = W[:, s]
            double z0 = W[0][s], z1 = W[1][s];
            double m = Math.max(z0, z1);
            double e0 = Math.exp(z0 - m);
            double e1 = Math.exp(z1 - m);
            double Z = e0 + e1;
            return new double[] { e0 / Z, e1 / Z };
        }

        int sampleAction(double[] p) {
            double u = rnd.nextDouble();
            return (u < p[0]) ? 0 : 1;
        }

        double logProb(int s, int a, double[] p) {
            return Math.log(p[a] + 1e-12);
        }

        // rollout to collect transitions up to T steps or terminal
        List<Transition> rollout(GridWorld env, int T) {
            List<Transition> traj = new ArrayList<>();
            int s = env.reset(rnd);
            for (int t = 0; t < T; t++) {
                double[] p = probs(s);
                int a = sampleAction(p);
                double oldLogPi = logProb(s, a, p);
                Step step = env.step(a);
                traj.add(new Transition(s, a, step.r, step.s, oldLogPi));
                s = step.s;
                if (step.done)
                    break;
            }
            return traj;
        }

        // compute returns and GAE advantages on a single trajectory
        void computeGAE(List<Transition> traj) {
            int T = traj.size();
            double nextV = 0.0;
            double gae = 0.0;
            for (int t = T - 1; t >= 0; t--) {
                Transition tr = traj.get(t);
                boolean done = (tr.ns == 0 || tr.ns == S - 1);
                double v = V[tr.s];
                nextV = done ? 0.0 : V[tr.ns];
                double delta = tr.r + (done ? 0.0 : gamma * nextV) - v;
                gae = delta + gamma * lambda * (done ? 0.0 : gae);
                tr.adv = gae;
                tr.ret = v + gae; // target for value regression
            }
        }

        // normalize advantages across a big batch
        static void normalizeAdvantages(List<Transition> batch) {
            double mean = batch.stream().mapToDouble(t -> t.adv).average().orElse(0.0);
            double var = batch.stream().mapToDouble(t -> (t.adv - mean) * (t.adv - mean)).average().orElse(1e-8);
            double std = Math.sqrt(var + 1e-8);
            for (Transition tr : batch)
                tr.adv = (tr.adv - mean) / (std + 1e-8);
        }

        // One PPO update over a batch for K epochs, full-batch (no minibatch to keep
        // minimal)
        void ppoUpdate(List<Transition> batch, int K) {
            for (int epoch = 0; epoch < K; epoch++) {
                // grads
                double[][] gW = new double[A][S];
                double[] gV = new double[S];
                double pgLoss = 0, vLoss = 0, ent = 0;

                for (Transition tr : batch) {
                    double[] pNow = probs(tr.s);
                    double logpNow = Math.log(pNow[tr.a] + 1e-12);
                    double ratio = Math.exp(logpNow - tr.oldLogPi);

                    // clipped obj per-sample
                    double unclipped = ratio * tr.adv;
                    double clipped = Math.max(Math.min(ratio, 1.0 + clip), 1.0 - clip) * tr.adv;
                    double obj = Math.min(unclipped, clipped);
                    pgLoss += -obj; // we will minimize loss (so negative sign)

                    // entropy bonus
                    double sampleEnt = -(pNow[0] * Math.log(pNow[0] + 1e-12) + pNow[1] * Math.log(pNow[1] + 1e-12));
                    ent += sampleEnt;

                    // grad of policy: ∇(-obj) ≈ -(∂obj/∂logπ) * ∂logπ/∂W
                    // In clipped region where obj uses clipped ratio plateau, gradient to W is zero
                    // (for simplicity).
                    boolean inClippedBad = (tr.adv > 0 && ratio > 1.0 + clip) ||
                            (tr.adv < 0 && ratio < 1.0 - clip);

                    if (!inClippedBad) {
                        // ∂(-ratio*adv)/∂W = -(ratio*adv) * ∂logπ/∂W
                        // For tabular softmax on state s:
                        // ∂logπ(a|s)/∂W[k][s] = (1_{k=a} - pNow[k])
                        for (int k = 0; k < A; k++) {
                            double coeff = ((k == tr.a) ? 1.0 : 0.0) - pNow[k];
                            gW[k][tr.s] += -(ratio * tr.adv) * coeff;
                        }
                    }
                    // add entropy bonus gradient: -entropyCoef * ∂H/∂W
                    // For softmax tabular, ∂H/∂W[k][s] = - (log p_k + 1) * ∂p_k/∂W ... (too
                    // verbose)
                    // Simpler: encourage logits toward uniform by pushing against (p - 0.5)
                    for (int k = 0; k < A; k++) {
                        double target = 0.5;
                        gW[k][tr.s] += -entropyCoef * (pNow[k] - target);
                    }

                    // value loss: 0.5*(V - ret)^2
                    double diff = V[tr.s] - tr.ret;
                    vLoss += 0.5 * diff * diff;
                    gV[tr.s] += diff; // derivative wrt V[s]
                }

                int N = batch.size();
                // scale losses
                pgLoss /= N;
                vLoss = valueCoef * (vLoss / N);
                ent /= N;

                // gradient step (SGD)
                // (optional) grad clipping on policy & value separately
                double gNorm = 0.0;
                for (int a = 0; a < A; a++)
                    for (int s = 0; s < S; s++)
                        gNorm += gW[a][s] * gW[a][s];
                gNorm = Math.sqrt(gNorm);
                double scale = (gNorm > maxGradNorm) ? (maxGradNorm / (gNorm + 1e-9)) : 1.0;

                for (int a = 0; a < A; a++)
                    for (int s = 0; s < S; s++)
                        W[a][s] -= lrPi * scale * (gW[a][s] / N);

                // value grad clipping
                double gVNorm = 0.0;
                for (int s = 0; s < S; s++)
                    gVNorm += gV[s] * gV[s];
                gVNorm = Math.sqrt(gVNorm);
                double scaleV = (gVNorm > maxGradNorm) ? (maxGradNorm / (gVNorm + 1e-9)) : 1.0;

                for (int s = 0; s < S; s++)
                    V[s] -= lrV * scaleV * (gV[s] / N);
            }
        }
    }

    // =============== Training loop ===============
    public static void main(String[] args) {
        GridWorld env = new GridWorld();
        int S = env.N;
        PPOAgent agent = new PPOAgent(S);

        int iters = 300; // PPO iterations
        int episodesPerIter = 32;
        int horizon = 40; // max steps per episode
        int K = 4; // PPO epochs over the collected batch

        Random rnd = new Random(7);
        double emaReturn = 0.0;

        for (int it = 1; it <= iters; it++) {
            List<Transition> batch = new ArrayList<>();

            // --------- collect episodes ---------
            int success = 0;
            double sumReturn = 0.0;

            for (int ep = 0; ep < episodesPerIter; ep++) {
                List<Transition> traj = agent.rollout(env, horizon);
                // compute GAE on this trajectory
                agent.computeGAE(traj);
                batch.addAll(traj);

                // stats
                double G = traj.stream().mapToDouble(t -> t.r).sum();
                sumReturn += G;
                if (traj.size() > 0 && (traj.get(traj.size() - 1).ns == S - 1))
                    success++;
            }

            // normalize A
            PPOAgent.normalizeAdvantages(batch);

            // --------- PPO update ---------
            agent.ppoUpdate(batch, K);

            // --------- logging ---------
            double avgRet = sumReturn / episodesPerIter;
            emaReturn = 0.9 * emaReturn + 0.1 * avgRet;
            double succRate = success / (double) episodesPerIter;

            if (it % 10 == 0) {
                System.out.printf(
                        "Iter %3d | AvgReturn %.4f | EMA %.4f | Success %.2f | BatchSize %d%n",
                        it, avgRet, emaReturn, succRate, batch.size());
            }
        }

        System.out.println("Training done. You should see success rate improving over iterations.");
        System.out.println("Try tweaking clip/lr/gamma/lambda/K/episodesPerIter to observe stability.");
    }
}
