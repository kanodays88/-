package com.kanodays88.skytakeoutai.agent.router;

import com.kanodays88.skytakeoutai.skill.Skill;

import java.util.List;

public record RouteDecisionAndSkills(
        RouteDecision decision,
        List<Skill> skills
){}
