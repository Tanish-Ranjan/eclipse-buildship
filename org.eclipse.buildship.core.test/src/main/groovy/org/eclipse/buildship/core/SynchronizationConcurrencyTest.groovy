package org.eclipse.buildship.core

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.ICoreRunnable
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job

import org.eclipse.buildship.core.internal.CorePlugin
import org.eclipse.buildship.core.internal.event.Event
import org.eclipse.buildship.core.internal.event.EventListener
import org.eclipse.buildship.core.internal.test.fixtures.ProjectSynchronizationSpecification
import org.eclipse.buildship.core.internal.workspace.ProjectCreatedEvent

class SynchronizationConcurrencyTest extends ProjectSynchronizationSpecification {

    def "Concurrently executed synchronizations runs sequentially"() {
        setup:
        List<Job> syncJobs = (1..5).collect {
            new SyncJob(dir("location_$it") { file "build.gradle", "Thread.sleep(500)" })
        }
        ProjectCreationTracker tracker = new ProjectCreationTracker()
        CorePlugin.listenerRegistry().addEventListener(tracker)

        when:
        syncJobs.each { it.schedule() }
        syncJobs.each { it.join() }

        then:
        tracker.assertTimeBetweenProjectCreationsHigherThan(500)

        cleanup:
        CorePlugin.listenerRegistry().removeEventListener(tracker)
    }

    def "Synchronization requires workspace rule"() {
        setup:
        // import a sample project
        File location = dir('SynchronizationConcurrencyTest')
        importAndWait(location)

        Job syncJob = new SyncJob(location)
        when:
        // start and wait for synchronization with workspace root rule already used
        ICoreRunnable syncOperation = {
            syncJob.schedule()
            assert !syncJob.join(1000, new NullProgressMonitor())
        }
        workspace.run(syncOperation, workspace.root, IResource.NONE, new NullProgressMonitor())

        then:
        // synchronization won't start until the job with the workspace rule finishes
        syncJob.join(1000, new NullProgressMonitor())
    }

    class SyncJob extends Job {

        File location

        SyncJob(File location) {
            super('synchronization job')
            this.location = location;
        }

        protected IStatus run(IProgressMonitor monitor) {
            importAndWait(location)
            Status.OK_STATUS
        }
    }

    class ProjectCreationTracker implements EventListener {

        List durationsBetweenNewProjectEvents = []
        long lastEvent = -1

        @Override
        public void onEvent(Event event) {
             if (event instanceof ProjectCreatedEvent) {
                 long now = System.currentTimeMillis()
                 if (lastEvent < 0) {
                     lastEvent = now
                 } else {
                     durationsBetweenNewProjectEvents += (now - lastEvent)
                     lastEvent = now
                 }
             }
        }

        def assertTimeBetweenProjectCreationsHigherThan(long diff) {
            assert !durationsBetweenNewProjectEvents.empty
            durationsBetweenNewProjectEvents.each { assert it > diff }
        }
    }
}
