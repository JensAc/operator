package eu.glasskube.operator.matomo.dependent

import eu.glasskube.kubernetes.api.model.apps.deployment
import eu.glasskube.kubernetes.api.model.apps.selector
import eu.glasskube.kubernetes.api.model.apps.spec
import eu.glasskube.kubernetes.api.model.apps.template
import eu.glasskube.kubernetes.api.model.configMapRef
import eu.glasskube.kubernetes.api.model.configMapVolumeSource
import eu.glasskube.kubernetes.api.model.container
import eu.glasskube.kubernetes.api.model.containerPort
import eu.glasskube.kubernetes.api.model.envFrom
import eu.glasskube.kubernetes.api.model.metadata
import eu.glasskube.kubernetes.api.model.secretRef
import eu.glasskube.kubernetes.api.model.spec
import eu.glasskube.kubernetes.api.model.volume
import eu.glasskube.kubernetes.api.model.volumeMount
import eu.glasskube.operator.matomo.Matomo
import eu.glasskube.operator.matomo.MatomoReconciler
import eu.glasskube.operator.matomo.configMapName
import eu.glasskube.operator.matomo.deploymentName
import eu.glasskube.operator.matomo.identifyingLabel
import eu.glasskube.operator.matomo.resourceLabels
import eu.glasskube.operator.matomo.secretName
import io.fabric8.kubernetes.api.model.KeyToPath
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent

@KubernetesDependent(labelSelector = MatomoReconciler.SELECTOR)
class MatomoDeployment : CRUDKubernetesDependentResource<Deployment, Matomo>(Deployment::class.java) {

    private val matomoImage = "ghcr.io/glasskube/matomo-docker:4.12.3-apache.b"
    private val wwwDataVolumeName = "www-data"
    private val matomoConfigurationVolumeName = "matomo-configuration"

    override fun desired(primary: Matomo, context: Context<Matomo>) = deployment {
        metadata {
            name = primary.deploymentName
            namespace = primary.metadata.namespace
            labels = primary.resourceLabels
        }
        spec {
            selector {
                matchLabels = mapOf(primary.identifyingLabel)
            }
            template {
                metadata {
                    labels = primary.resourceLabels
                }
                spec {
                    initContainers = listOf(
                        container {
                            name = "matomo-init"
                            image = matomoImage
                            envFrom {
                                secretRef(primary.secretName)
                                configMapRef(primary.configMapName)
                            }
                            volumeMounts = listOf(
                                volumeMount {
                                    name = wwwDataVolumeName
                                    mountPath = "/var/www/html"
                                },
                                volumeMount {
                                    name = matomoConfigurationVolumeName
                                    mountPath = "/tmp/matomo"
                                    readOnly = true
                                }
                            )
                            command = listOf(
                                "sh",
                                "-c",
                                "ls -la /tmp/matomo" +
                                    " && echo test" +
                                    " && cat /tmp/matomo/install.json" +
                                    " && rsync -crlOt --no-owner --no-group --no-perms /usr/src/matomo/ /var/www/html/" +
                                    " && ./console plugin:activate ExtraTools && ./console matomo:install --install-file=/tmp/matomo/install.json --force --do-not-drop-db" +
                                    " && echo done"
                            )
                        }
                    )
                    containers = listOf(
                        container {
                            name = "matomo"
                            image = matomoImage
                            ports = listOf(containerPort { containerPort = 80 })
                            envFrom {
                                secretRef(primary.secretName)
                                configMapRef(primary.configMapName)
                            }
                            volumeMounts = listOf(
                                volumeMount {
                                    name = wwwDataVolumeName
                                    mountPath = "/var/www/html"
                                }
                            )
                        }
                    )
                    volumes = listOf(
                        volume {
                            name = wwwDataVolumeName
                            emptyDir
                        },
                        volume {
                            name = matomoConfigurationVolumeName
                            configMapVolumeSource {
                                name = primary.configMapName
                                items = listOf(
                                    KeyToPath("install.json", null, "/tmp/matomo/install.json")
                                )
                            }
                        }

                    )
                }
            }
        }
    }
}
