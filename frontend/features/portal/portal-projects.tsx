"use client";

import { ArrowRight, BriefcaseBusiness } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { ApiClientError } from "@/lib/api-client";
import { portalApi } from "./portal-api";
import type { PortalProject } from "./types";

export function PortalProjects() {
  const [projects, setProjects] = useState<PortalProject[]>();
  const [error, setError] = useState("");

  useEffect(() => {
    portalApi.projects().then(setProjects).catch((cause) => {
      setError(cause instanceof ApiClientError ? cause.message : "Projects could not be loaded.");
    });
  }, []);

  return (
    <>
      <div className="max-w-3xl">
        <p className="text-sm font-bold uppercase tracking-[0.15em] text-primary">Client portal</p>
        <h1 className="mt-3 text-3xl font-bold tracking-[-0.035em] sm:text-5xl">
          Your onboarding projects, with the next step made clear.
        </h1>
        <p className="mt-4 max-w-2xl text-base leading-7 text-muted-foreground">
          Open a project to see current status, progress, blockers, deadlines, and exactly who needs to act.
        </p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {!projects && !error && (
        <div className="mt-8 grid gap-4 md:grid-cols-2">
          {[0, 1].map((item) => <div key={item} className="h-52 animate-pulse rounded-xl bg-muted" />)}
        </div>
      )}

      {projects?.length === 0 && (
        <Card className="mt-8 text-center">
          <BriefcaseBusiness aria-hidden="true" className="mx-auto size-8 text-muted-foreground" />
          <h2 className="mt-4 text-xl font-bold">No projects are assigned</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Ask your project team to invite you to the correct onboarding.
          </p>
        </Card>
      )}

      <div className="mt-8 grid gap-4 md:grid-cols-2">
        {projects?.map((project) => (
          <Link
            key={project.id}
            href={`/portal/projects/${project.id}`}
            className="group cursor-pointer rounded-xl focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring"
          >
            <Card className="h-full transition-colors group-hover:border-primary/45">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-muted-foreground">{project.clientName}</p>
                  <h2 className="mt-2 text-xl font-bold">{project.name}</h2>
                </div>
                <Badge tone={project.pendingRequirements > 0 ? "warning" : "success"}>
                  {project.pendingRequirements} pending
                </Badge>
              </div>
              <div className="mt-7 flex items-end justify-between gap-4">
                <div className="flex-1">
                  <div className="flex justify-between text-xs font-semibold">
                    <span>Progress</span>
                    <span>{project.progress}%</span>
                  </div>
                  <div
                    role="progressbar"
                    aria-label={`${project.name} progress`}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={project.progress}
                    className="mt-2 h-2 overflow-hidden rounded-full bg-muted"
                  >
                    <div
                      className="h-full rounded-full bg-primary transition-[width] duration-300 motion-reduce:transition-none"
                      style={{ width: `${project.progress}%` }}
                    />
                  </div>
                </div>
                <ArrowRight
                  aria-hidden="true"
                  className="mb-0.5 size-5 text-primary transition-transform group-hover:translate-x-1 motion-reduce:transition-none"
                />
              </div>
              <p className="mt-4 text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                {project.onboardingStatus.replaceAll("_", " ")}
              </p>
            </Card>
          </Link>
        ))}
      </div>
    </>
  );
}
