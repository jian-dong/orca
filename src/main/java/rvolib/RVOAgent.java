package rvolib;

import cz.agents.alite.vis.VisManager;
import tt.euclid2i.EvaluatedTrajectory;
import tt.euclid2i.Point;
import tt.euclid2i.discretization.LazyGrid;
import util.ProblemProvider;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RVOAgent {

    private ArrayList<SimpleEntry<Float, RVOAgent>> agentNeighbors_ = new ArrayList<SimpleEntry<Float, RVOAgent>>();
    public int maxNeighbors_ = 0;
    public float maxSpeed_ = 0.0f;
    public float neighborDist_ = 0.0f;
    private Vector2 newVelocity_ = new Vector2();
    private List<SimpleEntry<Float, RVOObstacle>> obstacleNeighbors_ = new ArrayList<SimpleEntry<Float, RVOObstacle>>();
    public ArrayList<RVOLine> orcaLines_ = new ArrayList<RVOLine>();
    public Vector2 position_;
    public Vector2 prefVelocity_;
    public float radius_ = 0.0f;
    public float timeHorizon_ = 0.0f;
    public float timeHorizonObst_ = 0.0f;
    public Vector2 velocity_;
    public int id_ = 0;
    public ArrayList<Point> trajectory = new ArrayList<>();
    private LazyGrid graph;
    private HashMap<Point, Double> evaluatedGraph = new HashMap<>();
    private ArrayList<Point> closeList = new ArrayList<Point>();
    public boolean showVis = true;
    // goal used for visualization only
    public Point goal_;

    public void computeNeighbors() {
        obstacleNeighbors_.clear();

        float rangeSq = RVOMath.sqr(timeHorizonObst_ * maxSpeed_ + radius_);
        MutableFloat rangeSqMutable = new MutableFloat(rangeSq);
        Simulator.getInstance().getKdTree()
                .computeObstacleNeighbors(this, rangeSq);

        agentNeighbors_.clear();
        if (maxNeighbors_ > 0) {
            rangeSq = RVOMath.sqr(neighborDist_);
            Simulator.getInstance().getKdTree()
                    .computeAgentNeighbors(this, rangeSqMutable);
        }
    }

    /* Search for the best new velocity. */
    public void computeNewVelocity() {
        orcaLines_.clear();

        float invTimeHorizonObst = 1.0f / timeHorizonObst_;

		/* Create obstacle ORCA lines. */
        for (int i = 0; i < obstacleNeighbors_.size(); ++i) {

            RVOObstacle obstacle1 = obstacleNeighbors_.get(i).getValue();
            RVOObstacle obstacle2 = obstacle1.nextObstacle;

            Vector2 relativePosition1 = Vector2.minus(obstacle1.point_,
                    position_);
            Vector2 relativePosition2 = Vector2.minus(obstacle2.point_,
                    position_);

			/*
             * Check if velocity obstacle of obstacle is already taken care of
			 * by previously constructed obstacle ORCA lines.
			 */
            boolean alreadyCovered = false;

            for (int j = 0; j < orcaLines_.size(); ++j) {
                if (RVOMath.det(Vector2.minus(
                        Vector2.scale(invTimeHorizonObst, relativePosition1),
                        orcaLines_.get(j).point), orcaLines_.get(j).direction)
                        - invTimeHorizonObst * radius_ >= -RVOMath.RVO_EPSILON
                        && RVOMath.det(Vector2.minus(Vector2.scale(
                        invTimeHorizonObst, relativePosition2),
                        orcaLines_.get(j).point),
                        orcaLines_.get(j).direction)
                        - invTimeHorizonObst * radius_ >= -RVOMath.RVO_EPSILON) {
                    alreadyCovered = true;
                    break;
                }
            }

            if (alreadyCovered) {
                continue;
            }

			/* Not yet covered. Check for collisions. */

            float distSq1 = RVOMath.absSq(relativePosition1);
            float distSq2 = RVOMath.absSq(relativePosition2);

            float radiusSq = RVOMath.sqr(radius_);

            Vector2 obstacleVector = Vector2.minus(obstacle2.point_,
                    obstacle1.point_);
            float s = Vector2.dot(Vector2.invert(relativePosition1),
                    obstacleVector) / RVOMath.absSq(obstacleVector);
            float distSqLine = RVOMath.absSq(Vector2.minus(
                    Vector2.invert(relativePosition1),
                    Vector2.scale(s, obstacleVector)));

            RVOLine line = new RVOLine();

            if (s < 0 && distSq1 <= radiusSq) {
				/* Collision with left vertex. Ignore if non-convex. */
                if (obstacle1.isConvex_) {
                    line.point = new Vector2(0, 0);
                    line.direction = RVOMath.normalize(new Vector2(
                            -relativePosition1.y(), relativePosition1.x()));
                    orcaLines_.add(line);
                }
                continue;
            } else if (s > 1 && distSq2 <= radiusSq) {
				/*
				 * Collision with right vertex. Ignore if non-convex or if it
				 * will be taken care of by neighoring obstace
				 */
                if (obstacle2.isConvex_
                        && RVOMath.det(relativePosition2, obstacle2.unitDir_) >= 0) {
                    line.point = new Vector2(0, 0);
                    line.direction = RVOMath.normalize(new Vector2(
                            -relativePosition2.y(), relativePosition2.x()));
                    orcaLines_.add(line);
                }
                continue;
            } else if (s >= 0 && s < 1 && distSqLine <= radiusSq) {
				/* Collision with obstacle segment. */
                line.point = new Vector2(0, 0);
                line.direction = Vector2.invert(obstacle1.unitDir_);
                orcaLines_.add(line);
                continue;
            }

			/*
			 * No collision. Compute legs. When obliquely viewed, both legs can
			 * come from a single vertex. Legs extend cut-off line when
			 * nonconvex vertex.
			 */

            Vector2 leftLegDirection, rightLegDirection;

            if (s < 0 && distSqLine <= radiusSq) {
				/*
				 * Obstacle viewed obliquely so that left vertex defines
				 * velocity obstacle.
				 */
                if (!obstacle1.isConvex_) {
					/* Ignore obstacle. */
                    continue;
                }

                obstacle2 = obstacle1;

                float leg1 = RVOMath.sqrt(distSq1 - radiusSq);
                leftLegDirection = Vector2.divide(
                        new Vector2(relativePosition1.x() * leg1
                                - relativePosition1.y() * radius_,
                                relativePosition1.x() * radius_
                                        + relativePosition1.y() * leg1),
                        distSq1);
                rightLegDirection = Vector2.divide(new Vector2(
                        relativePosition1.x() * leg1 + relativePosition1.y()
                                * radius_, -relativePosition1.x() * radius_
                        + relativePosition1.y() * leg1), distSq1);
            } else if (s > 1 && distSqLine <= radiusSq) {
				/*
				 * Obstacle viewed obliquely so that right vertex defines
				 * velocity obstacle.
				 */
                if (!obstacle2.isConvex_) {
					/* Ignore obstacle. */
                    continue;
                }

                obstacle1 = obstacle2;

                float leg2 = RVOMath.sqrt(distSq2 - radiusSq);
                leftLegDirection = Vector2.divide(
                        new Vector2(relativePosition2.x() * leg2
                                - relativePosition2.y() * radius_,
                                relativePosition2.x() * radius_
                                        + relativePosition2.y() * leg2),
                        distSq2);
                rightLegDirection = Vector2.divide(new Vector2(
                        relativePosition2.x() * leg2 + relativePosition2.y()
                                * radius_, -relativePosition2.x() * radius_
                        + relativePosition2.y() * leg2), distSq2);
            } else {
				/* Usual situation. */
                if (obstacle1.isConvex_) {
                    float leg1 = RVOMath.sqrt(distSq1 - radiusSq);
                    leftLegDirection = Vector2.divide(
                            new Vector2(relativePosition1.x() * leg1
                                    - relativePosition1.y() * radius_,
                                    relativePosition1.x() * radius_
                                            + relativePosition1.y() * leg1),
                            distSq1);
                } else {
					/* Left vertex non-convex; left leg extends cut-off line. */
                    leftLegDirection = Vector2.invert(obstacle1.unitDir_);
                }

                if (obstacle2.isConvex_) {
                    float leg2 = RVOMath.sqrt(distSq2 - radiusSq);
                    rightLegDirection = Vector2.divide(
                            new Vector2(relativePosition2.x() * leg2
                                    + relativePosition2.y() * radius_,
                                    -relativePosition2.x() * radius_
                                            + relativePosition2.y() * leg2),
                            distSq2);
                } else {
					/* Right vertex non-convex; right leg extends cut-off line. */
                    rightLegDirection = obstacle1.unitDir_;
                }
            }

			/*
			 * Legs can never point into neighboring edge when convex vertex,
			 * take cutoff-line of neighboring edge instead. If velocity
			 * projected on "foreign" leg, no constraint is added.
			 */

            RVOObstacle leftNeighbor = obstacle1.prevObstacle;

            boolean isLeftLegForeign = false;
            boolean isRightLegForeign = false;

            if (obstacle1.isConvex_
                    && RVOMath.det(leftLegDirection,
                    Vector2.invert(leftNeighbor.unitDir_)) >= 0.0f) {
				/* Left leg points into obstacle. */
                leftLegDirection = Vector2.invert(leftNeighbor.unitDir_);
                isLeftLegForeign = true;
            }

            if (obstacle2.isConvex_
                    && RVOMath.det(rightLegDirection, obstacle2.unitDir_) <= 0.0f) {
				/* Right leg points into obstacle. */
                rightLegDirection = obstacle2.unitDir_;
                isRightLegForeign = true;
            }

			/* Compute cut-off centers. */
            Vector2 leftCutoff = Vector2.scale(invTimeHorizonObst,
                    Vector2.minus(obstacle1.point_, position_));
            Vector2 rightCutoff = Vector2.scale(invTimeHorizonObst,
                    Vector2.minus(obstacle2.point_, position_));
            Vector2 cutoffVec = Vector2.minus(rightCutoff, leftCutoff);

			/* Project current velocity on velocity obstacle. */

			/* Check if current velocity is projected on cutoff circles. */
            float t = (obstacle1 == obstacle2 ? 0.5f : Vector2.dot(
                    Vector2.minus(velocity_, leftCutoff), cutoffVec)
                    / RVOMath.absSq(cutoffVec));
            float tLeft = Vector2.dot(Vector2.minus(velocity_, leftCutoff),
                    leftLegDirection);
            float tRight = Vector2.dot(Vector2.minus(velocity_, rightCutoff),
                    rightLegDirection);

            if ((t < 0.0f && tLeft < 0.0f)
                    || (obstacle1 == obstacle2 && tLeft < 0.0f && tRight < 0.0f)) {
				/* Project on left cut-off circle. */
                Vector2 unitW = RVOMath.normalize(Vector2.minus(velocity_,
                        leftCutoff));

                line.direction = new Vector2(unitW.y(), -unitW.x());
                line.point = Vector2.plus(leftCutoff,
                        Vector2.scale(radius_ * invTimeHorizonObst, unitW));
                orcaLines_.add(line);
                continue;
            } else if (t > 1.0f && tRight < 0.0f) {
				/* Project on right cut-off circle. */
                Vector2 unitW = RVOMath.normalize(Vector2.minus(velocity_,
                        rightCutoff));

                line.direction = new Vector2(unitW.y(), -unitW.x());
                line.point = Vector2.plus(rightCutoff,
                        Vector2.scale(radius_ * invTimeHorizonObst, unitW));
                orcaLines_.add(line);
                continue;
            }

			/*
			 * Project on left leg, right leg, or cut-off line, whichever is
			 * closest to velocity.
			 */
            float distSqCutoff = ((t < 0.0f || t > 1.0f || obstacle1 == obstacle2) ? Float.MAX_VALUE
                    : RVOMath.absSq(Vector2.minus(
                    velocity_,
                    Vector2.plus(leftCutoff,
                            Vector2.scale(t, cutoffVec)))));
            float distSqLeft = ((tLeft < 0.0f) ? Float.MAX_VALUE : RVOMath
                    .absSq(Vector2.minus(
                            velocity_,
                            Vector2.plus(leftCutoff,
                                    Vector2.scale(tLeft, leftLegDirection)))));
            float distSqRight = ((tRight < 0.0f) ? Float.MAX_VALUE : RVOMath
                    .absSq(Vector2.minus(
                            velocity_,
                            Vector2.plus(rightCutoff,
                                    Vector2.scale(tRight, rightLegDirection)))));

            if (distSqCutoff <= distSqLeft && distSqCutoff <= distSqRight) {
				/* Project on cut-off line. */
                line.direction = Vector2.invert(obstacle1.unitDir_);
                line.point = Vector2.plus(leftCutoff, Vector2.scale(radius_
                        * invTimeHorizonObst, new Vector2(-line.direction.y(),
                        line.direction.x())));
                orcaLines_.add(line);
                continue;
            } else if (distSqLeft <= distSqRight) {
				/* Project on left leg. */
                if (isLeftLegForeign) {
                    continue;
                }

                line.direction = leftLegDirection;
                line.point = Vector2.plus(leftCutoff, Vector2.scale(radius_
                        * invTimeHorizonObst, new Vector2(-line.direction.y(),
                        line.direction.x())));
                orcaLines_.add(line);
                continue;
            } else {
				/* Project on right leg. */
                if (isRightLegForeign) {
                    continue;
                }

                line.direction = Vector2.invert(rightLegDirection);
                line.point = Vector2.plus(rightCutoff, Vector2.scale(radius_
                        * invTimeHorizonObst, new Vector2(-line.direction.y(),
                        line.direction.x())));
                orcaLines_.add(line);
                continue;
            }
        }

        int numObstLines = orcaLines_.size();

        float invTimeHorizon = 1.0f / timeHorizon_;

		/* Create agent ORCA lines. */
        for (int i = 0; i < agentNeighbors_.size(); ++i) {
            RVOAgent other = agentNeighbors_.get(i).getValue();

            Vector2 relativePosition = Vector2
                    .minus(other.position_, position_);
            Vector2 relativeVelocity = Vector2
                    .minus(velocity_, other.velocity_);
            float distSq = RVOMath.absSq(relativePosition);
            float combinedRadius = radius_ + other.radius_;
            float combinedRadiusSq = RVOMath.sqr(combinedRadius);

            RVOLine line = new RVOLine();
            Vector2 u;

            if (distSq > combinedRadiusSq) {
				/* No collision. */
                Vector2 w = Vector2.minus(relativeVelocity,
                        Vector2.scale(invTimeHorizon, relativePosition));
				/* Vector from cutoff center to relative velocity. */
                float wLengthSq = RVOMath.absSq(w);

                float dotProduct1 = Vector2.dot(w, relativePosition);

                if (dotProduct1 < 0.0f
                        && RVOMath.sqr(dotProduct1) > combinedRadiusSq
                        * wLengthSq) {
					/* Project on cut-off circle. */
                    float wLength = RVOMath.sqrt(wLengthSq);
                    Vector2 unitW = Vector2.divide(w, wLength);

                    line.direction = new Vector2(unitW.y(), -unitW.x());
                    u = Vector2.scale(
                            (combinedRadius * invTimeHorizon - wLength), unitW);
                } else {
					/* Project on legs. */
                    float leg = RVOMath.sqrt(distSq - combinedRadiusSq);

                    if (RVOMath.det(relativePosition, w) > 0.0f) {
						/* Project on left leg. */
                        line.direction = Vector2.divide(
                                new Vector2(
                                        relativePosition.x() * leg
                                                - relativePosition.y()
                                                * combinedRadius,
                                        relativePosition.x() * combinedRadius
                                                + relativePosition.y() * leg),
                                distSq);
                    } else {
						/* Project on right leg. */
                        line.direction = Vector2.invert(Vector2.divide(
                                new Vector2(
                                        relativePosition.x() * leg
                                                + relativePosition.y()
                                                * combinedRadius,
                                        -relativePosition.x() * combinedRadius
                                                + relativePosition.y() * leg),
                                distSq));
                    }

                    float dotProduct2 = Vector2.dot(relativeVelocity,
                            line.direction);

                    u = Vector2.minus(
                            Vector2.scale(dotProduct2, line.direction),
                            relativeVelocity);
                }
            } else {
				/* Collision. Project on cut-off circle of time timeStep. */
                float invTimeStep = 1.0f / Simulator.getInstance().timeStep_;

				/* Vector from cutoff center to relative velocity. */
                Vector2 w = Vector2.minus(relativeVelocity,
                        Vector2.scale(invTimeStep, relativePosition));

                float wLength = RVOMath.abs(w);
                Vector2 unitW = Vector2.divide(w, wLength);

                line.direction = new Vector2(unitW.y(), -unitW.x());
                u = Vector2.scale((combinedRadius * invTimeStep - wLength),
                        unitW);
            }

            line.point = Vector2.plus(velocity_, Vector2.scale(0.5f, u));
            orcaLines_.add(line);
        }

        int lineFail = linearProgram2(orcaLines_, maxSpeed_, prefVelocity_,
                false, newVelocity_);

        if (lineFail < orcaLines_.size()) {
            linearProgram3(orcaLines_, numObstLines, lineFail, maxSpeed_,
                    newVelocity_);
        }
    }

    public void setPrefVelocity(Vector2 velocity) {
//		System.out.println("pref velocity: " + velocity);
        this.prefVelocity_ = velocity;
    }

    public void insertAgentNeighbor(RVOAgent agent, MutableFloat rangeSq) {
        if (this != agent) {
            float distSq = RVOMath.absSq(Vector2.minus(position_,
                    agent.position_));

            if (distSq < rangeSq.getValue()) {
                if (agentNeighbors_.size() < maxNeighbors_) {
                    agentNeighbors_.add(new SimpleEntry<Float, RVOAgent>(
                            distSq, agent));
                }
                int i = agentNeighbors_.size() - 1;
                while (i != 0 && distSq < agentNeighbors_.get(i - 1).getKey()) {
                    agentNeighbors_.set(i, agentNeighbors_.get(i - 1));
                    --i;
                }
                agentNeighbors_.set(i, new SimpleEntry<Float, RVOAgent>(distSq,
                        agent));

                if (agentNeighbors_.size() == maxNeighbors_) {
                    rangeSq.setValue(agentNeighbors_.get(
                            agentNeighbors_.size() - 1).getKey());
                }
            }
        }
    }

    public void insertObstacleNeighbor(RVOObstacle obstacle, float rangeSq) {
        RVOObstacle nextObstacle = obstacle.nextObstacle;

        float distSq = RVOMath.distSqPointLineSegment(obstacle.point_,
                nextObstacle.point_, position_);

        if (distSq < rangeSq) {
            obstacleNeighbors_.add(new SimpleEntry<Float, RVOObstacle>(distSq,
                    obstacle));

            int i = obstacleNeighbors_.size() - 1;
            while (i != 0 && distSq < obstacleNeighbors_.get(i - 1).getKey()) {
                obstacleNeighbors_.set(i, obstacleNeighbors_.get(i - 1));
                --i;
            }
            obstacleNeighbors_.set(i, new SimpleEntry<Float, RVOObstacle>(
                    distSq, obstacle));
        }
    }

    public void update() {
//		System.out.println("new velocity: " + newVelocity_);
        velocity_ = newVelocity_;
        // System.out.println(velocity_.getLength());
        position_ = Vector2.plus(position_,
                Vector2.scale(Simulator.getInstance().timeStep_, velocity_));
        trajectory.add(new Point(Math.round(position_.x_), Math
                .round(position_.y_)));
        // System.out.println(this.id_+" new position: "+position_.toString());
    }

    public EvaluatedTrajectory getEvaluatedTrajectory() {
        return new RVOTrajectory(trajectory, Simulator.getInstance()
                .getTimeStep(), ProblemProvider.getInstance().getProblem().getTarget(id_));
    }

    boolean linearProgram1(ArrayList<RVOLine> lines, int lineNo, float radius,
                           Vector2 optVelocity, boolean directionOpt, Vector2 result) {
        float dotProduct = Vector2.dot(lines.get(lineNo).point,
                lines.get(lineNo).direction);
        float discriminant = RVOMath.sqr(dotProduct) + RVOMath.sqr(radius)
                - RVOMath.absSq(lines.get(lineNo).point);

        if (discriminant < 0.0f) {
			/* Max speed circle fully invalidates line lineNo. */
            return false;
        }

        float sqrtDiscriminant = RVOMath.sqrt(discriminant);
        float tLeft = -dotProduct - sqrtDiscriminant;
        float tRight = -dotProduct + sqrtDiscriminant;

        for (int i = 0; i < lineNo; ++i) {
            float denominator = RVOMath.det(lines.get(lineNo).direction,
                    lines.get(i).direction);
            float numerator = RVOMath.det(lines.get(i).direction,
                    Vector2.minus(lines.get(lineNo).point, lines.get(i).point));

            if (RVOMath.fabs(denominator) <= RVOMath.RVO_EPSILON) {
				/* Lines lineNo and i are (almost) parallel. */
                if (numerator < 0.0f) {
                    return false;
                } else {
                    continue;
                }
            }

            float t = numerator / denominator;

            if (denominator >= 0.0f) {
				/* Line i bounds line lineNo on the right. */
                tRight = Math.min(tRight, t);
            } else {
				/* Line i bounds line lineNo on the left. */
                tLeft = Math.max(tLeft, t);
            }

            if (tLeft > tRight) {
                return false;
            }
        }

        if (directionOpt) {
			/* Optimize direction. */
            if (Vector2.dot(optVelocity, lines.get(lineNo).direction) > 0.0f) {
				/* Take right extreme. */
                Vector2 resultLoc = Vector2.plus(lines.get(lineNo).point,
                        Vector2.scale(tRight, lines.get(lineNo).direction));
                Vector2.copy(result, resultLoc);
            } else {
				/* Take left extreme. */
                Vector2 resultLoc = Vector2.plus(lines.get(lineNo).point,
                        Vector2.scale(tLeft, lines.get(lineNo).direction));
                Vector2.copy(result, resultLoc);
            }
        } else {
			/* Optimize closest point. */
            float t = Vector2.dot(lines.get(lineNo).direction,
                    Vector2.minus(optVelocity, lines.get(lineNo).point));

            if (t < tLeft) {
                Vector2 resultLoc = Vector2.plus(lines.get(lineNo).point,
                        Vector2.scale(tLeft, lines.get(lineNo).direction));
                Vector2.copy(result, resultLoc);
            } else if (t > tRight) {
                Vector2 resultLoc = Vector2.plus(lines.get(lineNo).point,
                        Vector2.scale(tRight, lines.get(lineNo).direction));
                Vector2.copy(result, resultLoc);
            } else {
                Vector2 resultLoc = Vector2.plus(lines.get(lineNo).point,
                        Vector2.scale(t, lines.get(lineNo).direction));
                Vector2.copy(result, resultLoc);
            }
        }

        return true;
    }

    private int linearProgram2(ArrayList<RVOLine> lines, float radius,
                               Vector2 optVelocity, boolean directionOpt, Vector2 result) {
        if (directionOpt) {
			/*
			 * Optimize direction. Note that the optimization velocity is of
			 * unit length in this case.
			 */
            Vector2 resultLoc = Vector2.scale(radius, optVelocity);
            Vector2.copy(result, resultLoc);
        } else if (RVOMath.absSq(optVelocity) > RVOMath.sqr(radius)) {
			/* Optimize closest point and outside circle. */
            Vector2 resultLoc = Vector2.scale(radius,
                    RVOMath.normalize(optVelocity));
            Vector2.copy(result, resultLoc);
        } else {
			/* Optimize closest point and inside circle. */
            Vector2 resultLoc = optVelocity;
            Vector2.copy(result, resultLoc);
        }

        for (int i = 0; i < lines.size(); ++i) {
            if (RVOMath.det(lines.get(i).direction,
                    Vector2.minus(lines.get(i).point, result)) > 0.0f) {
				/*
				 * Result does not satisfy constraint i. Compute new optimal
				 * result.
				 */
                Vector2 tempResult = result;
                if (!linearProgram1(lines, i, radius, optVelocity,
                        directionOpt, result)) {
                    result = tempResult;
                    return i;
                }
            }
        }

        return lines.size();
    }

    private void linearProgram3(ArrayList<RVOLine> lines, int numObstLines,
                                int beginLine, float radius, Vector2 result) {
        float distance = 0.0f;

        for (int i = beginLine; i < lines.size(); ++i) {
            if (RVOMath.det(lines.get(i).direction,
                    Vector2.minus(lines.get(i).point, result)) > distance) {
				/* Result does not satisfy constraint of line i. */
                // std::vector<Line> projLines(lines.begin(), lines.begin() +
                // numObstLines);
                ArrayList<RVOLine> projLines = new ArrayList<RVOLine>();
                for (int ii = 0; ii < numObstLines; ++ii) {
                    projLines.add(lines.get(ii));
                }

                for (int j = numObstLines; j < i; ++j) {
                    RVOLine line = new RVOLine();

                    float determinant = RVOMath.det(lines.get(i).direction,
                            lines.get(j).direction);

                    if (RVOMath.fabs(determinant) <= RVOMath.RVO_EPSILON) {
						/* Line i and line j are parallel. */
                        if (Vector2.dot(lines.get(i).direction,
                                lines.get(j).direction) > 0.0f) {
							/* Line i and line j point in the same direction. */
                            continue;
                        } else {
							/* Line i and line j point in opposite direction. */
                            line.point = Vector2.scale(
                                    0.5f,
                                    Vector2.plus(lines.get(i).point,
                                            lines.get(j).point));
                        }
                    } else {
                        line.point = Vector2
                                .plus(lines.get(i).point,
                                        Vector2.scale(
                                                (RVOMath.det(
                                                        lines.get(j).direction,
                                                        Vector2.minus(
                                                                lines.get(i).point,
                                                                lines.get(j).point)) / determinant),
                                                lines.get(i).direction));
                    }

                    line.direction = RVOMath.normalize(Vector2.minus(
                            lines.get(j).direction, lines.get(i).direction));
                    projLines.add(line);
                }

                Vector2 tempResult = result;
                if (linearProgram2(projLines, radius,
                        new Vector2(-lines.get(i).direction.y(),
                                lines.get(i).direction.x()), true, result) < projLines
                        .size()) {
					/*
					 * This should in principle not happen. The result is by
					 * definition already in the feasible region of this linear
					 * program. If it fails, it is due to small floating point
					 * error, and the current result is kept.
					 */
                    result = tempResult;
                }

                distance = RVOMath.det(lines.get(i).direction,
                        Vector2.minus(lines.get(i).point, result));
            }
        }
    }

    public void initVisualization() {
        VisManager.registerLayer(RVOAgentLayer.create(this));
    }

    public void setGraph(LazyGrid grid) {
        this.graph = grid;
    }

    public void setEvaluatedGraph(HashMap<Point, Double> evaluatedGraph) {
        this.evaluatedGraph = evaluatedGraph;
    }

    public HashMap<Point, Double> getEvaluatedGraph() {
        return evaluatedGraph;
    }

    public ArrayList<Point> getCloseList() {
        return this.closeList;
    }

    public void clearTrajectory() {
        this.trajectory = new ArrayList<Point>();
    }

}