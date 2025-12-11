{{/*
Expand the name of the chart.
*/}}
{{- define "kuvasz.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "kuvasz.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "kuvasz.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "kuvasz.labels" -}}
helm.sh/chart: {{ include "kuvasz.chart" . }}
{{ include "kuvasz.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "kuvasz.selectorLabels" -}}
app.kubernetes.io/name: {{ include "kuvasz.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "kuvasz.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (include "kuvasz.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Determine the database hostname
*/}}
{{- define "kuvasz.databaseHost" -}}
{{- if .Values.postgresql.enabled -}}
{{- printf "%s-%s" .Release.Name "postgresql" | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- .Values.externalDatabase.host -}}
{{- end -}}
{{- end -}}

{{/*
Determine the database port
*/}}
{{- define "kuvasz.databasePort" -}}
{{- if .Values.postgresql.enabled -}}
5432
{{- else -}}
{{- .Values.externalDatabase.port -}}
{{- end -}}
{{- end -}}

{{/*
Determine the database name
*/}}
{{- define "kuvasz.databaseName" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.database -}}
{{- else -}}
{{- .Values.externalDatabase.database -}}
{{- end -}}
{{- end -}}

{{/*
Determine the database user
*/}}
{{- define "kuvasz.databaseUser" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.username -}}
{{- else -}}
{{- .Values.externalDatabase.user -}}
{{- end -}}
{{- end -}}

{{/*
Determine the database secret name
*/}}
{{- define "kuvasz.databaseSecretName" -}}
{{- if .Values.postgresql.enabled -}}
{{- if .Values.postgresql.auth.existingSecret -}}
{{- .Values.postgresql.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name "postgresql" | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- else -}}
{{- if .Values.externalDatabase.existingSecret -}}
{{- .Values.externalDatabase.existingSecret -}}
{{- else -}}
{{- printf "%s-%s" (include "kuvasz.fullname" .) "database" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Determine the database password key in secret
*/}}
{{- define "kuvasz.databasePasswordKey" -}}
{{- if .Values.postgresql.enabled -}}
{{- if .Values.postgresql.auth.existingSecretPasswordKey }}
{{- .Values.postgresql.auth.existingSecretPasswordKey -}}
{{- else -}}
postgresql-password
{{- end -}}
{{- else -}}
{{- .Values.externalDatabase.existingSecretPasswordKey -}}
{{- end -}}
{{- end -}}

{{/*
Determine the admin credentials secret name
*/}}
{{- define "kuvasz.adminSecretName" -}}
{{- printf "%s-%s" (include "kuvasz.fullname" .) "admin" -}}
{{- end -}}

