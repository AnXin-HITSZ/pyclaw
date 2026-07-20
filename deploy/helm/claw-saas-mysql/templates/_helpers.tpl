{{/*
Expand the chart name.
*/}}
{{- define "claw-saas-mysql.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "claw-saas-mysql.fullname" -}}
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
{{- define "claw-saas-mysql.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels.
*/}}
{{- define "claw-saas-mysql.labels" -}}
helm.sh/chart: {{ include "claw-saas-mysql.chart" . }}
{{ include "claw-saas-mysql.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels.
*/}}
{{- define "claw-saas-mysql.selectorLabels" -}}
app.kubernetes.io/name: {{ include "claw-saas-mysql.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Secret containing MYSQL_* bootstrap credentials.
*/}}
{{- define "claw-saas-mysql.secretName" -}}
{{- if .Values.mysql.auth.existingSecret -}}
{{- .Values.mysql.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-secret" (include "claw-saas-mysql.fullname" .) -}}
{{- end -}}
{{- end -}}
