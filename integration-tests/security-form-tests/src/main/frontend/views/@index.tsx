import {ViewConfig} from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
    menu: {order: 0, icon: 'line-awesome/svg/lock-open-solid.svg'},
    title: 'Hilla - Public',
};

export default function HillaPublicView() {
    return (
        <div className="flex flex-col h-full items-center justify-center p-l text-center box-border">
            <img style={{width: '200px'}} src="images/empty-plant.png"/>
            <h2>Hilla - Public</h2>
            <p>Everybody can see this page</p>
        </div>
    );
}
