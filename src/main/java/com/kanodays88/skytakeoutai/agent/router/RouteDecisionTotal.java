package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.skill.Skill;
import org.springframework.ai.document.Document;

import java.util.List;

public record RouteDecisionTotal(
        RouteDecision decision,
        List<Skill> skills
){}
