import { Navigate, Route, Routes } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import Login from './pages/Login';
import Workbench from './pages/Workbench';
import Teams from './pages/Teams';
import ProjectList from './pages/project/ProjectList';
import ProjectDetail from './pages/project/ProjectDetail';
import ModelProviders from './pages/catalog/ModelProviders';
import ModelServices from './pages/catalog/ModelServices';
import KnowledgeBases from './pages/catalog/KnowledgeBases';
import AssetList from './pages/asset/AssetList';
import AssetDetail from './pages/asset/AssetDetail';
import AssetUpload from './pages/asset/AssetUpload';
import VersionManager from './pages/asset/VersionManager';
import HttpApiTools from './pages/asset/HttpApiTools';
import AgentSquare from './pages/square/AgentSquare';
import SkillSquare from './pages/square/SkillSquare';
import AgentList from './pages/agent/AgentList';
import AgentWizard from './pages/agent/AgentWizard';
import AgentDetail from './pages/agent/AgentDetail';
import ObservabilityDashboard from './pages/observability/Dashboard';
import Artifacts from './pages/delivery/Artifacts';
import DeployTargets from './pages/delivery/DeployTargets';
import AgentLevels from './pages/delivery/AgentLevels';
import ReleaseList from './pages/delivery/ReleaseList';
import ReleaseDetail from './pages/delivery/ReleaseDetail';
import Routes_ from './pages/governance/Routes';
import CallerPolicies from './pages/governance/CallerPolicies';
import McpPolicies from './pages/governance/McpPolicies';
import ServiceDirectory from './pages/governance/ServiceDirectory';
import Components from './pages/ops/Components';
import Permissions from './pages/settings/Permissions';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<MainLayout />}>
        <Route path="/" element={<Workbench />} />
        <Route path="/projects" element={<ProjectList />} />
        <Route path="/projects/:id" element={<ProjectDetail />} />
        <Route path="/teams" element={<Teams />} />
        <Route path="/square/agents" element={<AgentSquare />} />
        <Route path="/square/skills" element={<SkillSquare />} />
        <Route path="/assets" element={<AssetList />} />
        <Route path="/assets/upload" element={<AssetUpload />} />
        <Route path="/assets/versions" element={<VersionManager />} />
        <Route path="/assets/http-api" element={<HttpApiTools />} />
        <Route path="/assets/:id" element={<AssetDetail />} />
        <Route path="/agents" element={<AgentList />} />
        <Route path="/agents/new" element={<AgentWizard />} />
        <Route path="/agents/:id" element={<AgentDetail />} />
        <Route path="/observability" element={<ObservabilityDashboard />} />
        <Route path="/obs/*" element={<Navigate to="/observability" replace />} />
        <Route path="/delivery/artifacts" element={<Artifacts />} />
        <Route path="/delivery/targets" element={<DeployTargets />} />
        <Route path="/delivery/levels" element={<AgentLevels />} />
        <Route path="/delivery/releases" element={<ReleaseList />} />
        <Route path="/releases/:id" element={<ReleaseDetail />} />
        <Route path="/gov/routes" element={<Routes_ />} />
        <Route path="/gov/caller-policies" element={<CallerPolicies />} />
        <Route path="/gov/mcp-policies" element={<McpPolicies />} />
        <Route path="/gov/directory" element={<ServiceDirectory />} />
        <Route path="/ops/components" element={<Components />} />
        <Route path="/settings/permissions" element={<Permissions />} />
        <Route path="/catalog/providers" element={<ModelProviders />} />
        <Route path="/catalog/services" element={<ModelServices />} />
        <Route path="/catalog/kbs" element={<KnowledgeBases />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
