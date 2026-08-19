# Oracle Cloud deployment

This runs the Agent Framework service on an Oracle Cloud Compute VM. Nothing runs on your desktop.

## One-time OCI setup

1. Use an Ubuntu or Oracle Linux Compute instance with a public IPv4 address.
2. Add inbound TCP rules for ports 80 and 443 in the VM subnet security list or network security group. Keep port 22 restricted to your own IP when possible.
3. Add a DNS A record such as agent.example.com pointing to the VM public IP.
4. Connect to the VM with SSH. Oracle Linux normally uses user opc; Ubuntu normally uses user ubuntu.

## Install and start

    sudo apt-get update
    sudo apt-get install -y git docker.io docker-compose-plugin
    sudo systemctl enable --now docker
    git clone --branch Field_Opps_V2.5 https://github.com/Strobingn/wildlife-fieldops-modern-ui.git
    cd wildlife-fieldops-modern-ui/agent-framework-service
    cp .env.oracle.example .env
    cp Caddyfile.example Caddyfile

Edit .env and set AGENT_DOMAIN, AGENT_FRAMEWORK_API_KEY, AGENT_FRAMEWORK_MODEL, and a long random AGENT_FRAMEWORK_SHARED_SECRET. Then start it:

    sudo docker compose -f docker-compose.oracle.yml up -d --build

Caddy obtains and renews HTTPS automatically after DNS points to the VM and ports 80/443 are reachable.

## Connect Supabase

In Supabase Edge Function secrets, set:

- AGENT_FRAMEWORK_URL=https://your-agent-domain.example.com
- AGENT_FRAMEWORK_SHARED_SECRET=the exact same value used on the VM

Then verify:

    curl https://your-agent-domain.example.com/health

Do not commit .env or any model/API key.
