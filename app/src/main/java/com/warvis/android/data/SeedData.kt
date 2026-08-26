package com.warvis.android.data

import com.warvis.android.data.model.CareerStage
import com.warvis.android.data.model.KnowledgeRing
import com.warvis.android.data.model.TaskCategory
import com.warvis.android.data.model.TaskPriority
import com.warvis.android.data.model.WarvisState
import com.warvis.android.data.model.WarvisTask

object SeedData {
    fun initialState(): WarvisState =
        WarvisState(
            tasks = complianceTasks() + sprintTasks(),
            knowledgeRings = knowledgeRings(),
            careerStages = careerStages(),
        )

    private fun complianceTasks(): List<WarvisTask> =
        listOf(
            task("c1", 0, 1, "Read employment contract", "Review outside-work, confidentiality, and IP assignment clauses.", TaskCategory.COMPLIANCE, TaskPriority.CRITICAL, 30),
            task("c2", 0, 2, "Read Deutsche Borse policy", "Review employee conduct and outside-business-activities policy.", TaskCategory.COMPLIANCE, TaskPriority.CRITICAL, 20),
            task("c3", 0, 3, "List forbidden topics", "Capture clients, competitors, internal roadmap, Camp AIR details, pricing, and confidential decks as off-limits.", TaskCategory.COMPLIANCE, TaskPriority.CRITICAL, 20),
            task("c4", 0, 4, "Get written manager/P&C approval", "Obtain written approval before expert-network registration or paid external calls.", TaskCategory.COMPLIANCE, TaskPriority.CRITICAL, 30),
            task("c5", 0, 5, "Define safe expert profile", "Use pre-SimCorp IB background and public financial-infrastructure knowledge only.", TaskCategory.COMPLIANCE, TaskPriority.HIGH, 20),
            task("c6", 0, 6, "Write per-call conduct rules", "Define how to redirect if a call approaches forbidden topics.", TaskCategory.COMPLIANCE, TaskPriority.HIGH, 15),
            task("c7", 0, 7, "Review anti-solicitation clauses", "Check GLG, AlphaSights, Guidepoint, and Fintalent non-solicitation/non-circumvention rules.", TaskCategory.COMPLIANCE, TaskPriority.HIGH, 30),
            task("c8", 0, 8, "Confirm PI insurance plan", "Identify professional indemnity cover needed before any TDD report delivery.", TaskCategory.COMPLIANCE, TaskPriority.MEDIUM, 20),
        )

    private fun sprintTasks(): List<WarvisTask> =
        listOf(
            task("w1-1", 1, 1, "Update LinkedIn headline", "Use AI Champion positioning now; do not overclaim PE/TDD mandate experience.", TaskCategory.LINKEDIN, TaskPriority.HIGH, 30, requiresCompliance = false),
            task("w1-2", 1, 2, "Update LinkedIn About section", "Frame financial infrastructure, AI Champion work, and PE-adjacent direction safely.", TaskCategory.LINKEDIN, TaskPriority.HIGH, 60, requiresCompliance = false),
            task("w1-3", 1, 3, "Register on AlphaSights", "Create expert profile only after compliance gate is cleared.", TaskCategory.EXPERT_NETWORKS, TaskPriority.HIGH, 30),
            task("w1-4", 1, 4, "Register on GLG and Guidepoint", "Create profiles using safe, public, pre-cleared expertise boundaries.", TaskCategory.EXPERT_NETWORKS, TaskPriority.HIGH, 45),
            task("w1-5", 1, 5, "Register on Fintalent.io", "Position as financial-infrastructure and AI transformation specialist.", TaskCategory.EXPERT_NETWORKS, TaskPriority.MEDIUM, 30),
            task("w2-1", 2, 1, "Read Mastering Private Equity chapters 1-4", "Build PE vocabulary and deal-process fluency.", TaskCategory.PE_KNOWLEDGE, TaskPriority.MEDIUM, 120, requiresCompliance = false),
            task("w2-2", 2, 2, "Draft TDD one-pager", "Describe a safe technical diligence offer around IBOR, AI-native architecture, and operating risk.", TaskCategory.TDD_INFRASTRUCTURE, TaskPriority.HIGH, 120),
            task("w2-3", 2, 3, "Identify 10 Nordic PE-backed fintech targets", "Research target companies without using confidential employer information.", TaskCategory.TDD_INFRASTRUCTURE, TaskPriority.HIGH, 120),
            task("w2-4", 2, 4, "Explore Danish ApS setup", "Capture basic company setup requirements, costs, and accountant questions.", TaskCategory.FINANCIAL_SETUP, TaskPriority.MEDIUM, 60, requiresCompliance = false),
            task("w3-1", 3, 1, "Message 3 PE-adjacent contacts", "Use modest wording: exploring AI and financial-infrastructure value creation.", TaskCategory.OUTREACH, TaskPriority.HIGH, 45),
            task("w3-2", 3, 2, "Draft Article 1", "Draft The Bottleneck Has Moved using public and approved sources only.", TaskCategory.ARTICLE, TaskPriority.HIGH, 120),
            task("w3-3", 3, 3, "Run article compliance self-check", "Check for employer, client, roadmap, and internal project references before publishing.", TaskCategory.COMPLIANCE, TaskPriority.CRITICAL, 30),
            task("w3-4", 3, 4, "Find Nordic PE or fintech events", "Identify events that can compound relationship capital.", TaskCategory.OUTREACH, TaskPriority.MEDIUM, 45, requiresCompliance = false),
            task("w4-1", 4, 1, "Prepare expert-call briefing script", "Write safe boundaries, redirect language, and approved expertise summary.", TaskCategory.EXPERT_NETWORKS, TaskPriority.HIGH, 60),
            task("w4-2", 4, 2, "Score all five knowledge rings", "Run an honest weekly self-assessment across the five gap areas.", TaskCategory.REVIEW, TaskPriority.HIGH, 30, requiresCompliance = false),
            task("w4-3", 4, 3, "Book or plan London relationship trip", "Outline meetings, target firms, and timing before committing money.", TaskCategory.OUTREACH, TaskPriority.MEDIUM, 60, requiresCompliance = false),
            task("w4-4", 4, 4, "Create Month 2 plan", "Convert what worked in the sprint into a second-month execution plan.", TaskCategory.REVIEW, TaskPriority.HIGH, 90, requiresCompliance = false),
        )

    private fun task(
        id: String,
        weekNumber: Int,
        order: Int,
        title: String,
        description: String,
        category: TaskCategory,
        priority: TaskPriority,
        estimatedMinutes: Int?,
        requiresCompliance: Boolean = true,
    ): WarvisTask =
        WarvisTask(
            id = id,
            weekNumber = weekNumber,
            weekTitle = if (weekNumber == 0) "Compliance Gate" else "Week $weekNumber",
            order = order,
            title = title,
            description = description,
            category = category,
            priority = priority,
            estimatedMinutes = estimatedMinutes,
            sourceLabel = if (weekNumber == 0) "Bridge Plan v4 Step 0" else "4-Week Sprint Plan v4",
            requiresCompliance = requiresCompliance,
        )

    private fun knowledgeRings(): List<KnowledgeRing> =
        listOf(
            KnowledgeRing("kr1", 1, "Investment Management Operations", "Gap: 12-18 months. Build operations fluency around asset management workflows.", 1, "Seeded"),
            KnowledgeRing("kr2", 2, "Regulatory Environment", "Gap: 6-9 months. Focus on DORA, EMIR, MiFID, and operational resilience.", 1, "Seeded"),
            KnowledgeRing("kr3", 3, "Financial Modelling", "Gap: 3-6 months. Build LBO, valuation, and PE scenario fluency.", 1, "Seeded"),
            KnowledgeRing("kr4", 4, "PE Process and Mechanics", "Gap: 6-12 months. Learn deal process, diligence, fund economics, and value creation.", 1, "Seeded"),
            KnowledgeRing("kr5", 5, "AI in Financial Services", "Current edge. Maintain AI Champion advantage and translate it into PE language.", 4, "Seeded"),
        )

    private fun careerStages(): List<CareerStage> =
        listOf(
            CareerStage("stage1", 1, "Positioning + Expert Networks", "Now-Month 18", "Build public positioning, expert-network presence, and safe PE-adjacent credibility.", isActive = true),
            CareerStage("stage2", 2, "First TDD Projects", "Month 6-18", "Move from expert calls to compliant co-delivered technical diligence work.", isActive = false),
            CareerStage("stage3", 3, "Portco CTO / VP Eng / AI Lead", "Year 2-3", "Non-skippable operator credential: build P&L, board, and team leadership evidence.", isActive = false, isNonSkippable = true),
            CareerStage("stage4", 4, "Regular TDD + Fractional OP", "Year 3-5", "Convert repeatable TDD and operator evidence into fractional operating partner work.", isActive = false),
            CareerStage("stage5", 5, "Full Operating Partner", "Year 5+", "Earn fund-level value-creation role with repeatable AI/technology transformation evidence.", isActive = false),
            CareerStage("stage6", 6, "Scout / Angel Investor", "Year 8-10+", "Use PE network, capital, and thesis platform to become a deep-tech scout or angel.", isActive = false),
        )
}
